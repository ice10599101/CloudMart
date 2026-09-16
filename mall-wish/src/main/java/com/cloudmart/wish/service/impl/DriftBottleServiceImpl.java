package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.ThrowBottleRequest;
import com.cloudmart.wish.entity.DriftBottle;
import com.cloudmart.wish.entity.DriftBottleInteraction;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.enums.AuditStatus;
import com.cloudmart.wish.enums.DriftBottleStatus;
import com.cloudmart.wish.enums.ResourceLogSource;
import com.cloudmart.wish.enums.WishStatus;
import com.cloudmart.wish.enums.WishVisibility;
import com.cloudmart.wish.mq.EncounterEventProducer;
import com.cloudmart.wish.repository.DriftBottleInteractionMapper;
import com.cloudmart.wish.repository.DriftBottleMapper;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.service.DriftBottleService;
import com.cloudmart.wish.service.UserStatService;
import com.cloudmart.wish.util.WishJsonUtils;
import com.cloudmart.wish.vo.DriftBottleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 漂流瓶服务实现。
 *
 * <p>捞瓶并发安全：随机候选 + 条件 UPDATE（status=FLOATING → PICKED），
 * 更新未命中即被他人并发捞走，进入下一轮重试（最多 {@value #FISH_ATTEMPTS} 次）。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DriftBottleServiceImpl implements DriftBottleService {

    private static final int LIGHT_COST = 2;
    private static final int FISH_ATTEMPTS = 3;

    private final DriftBottleMapper bottleMapper;
    private final DriftBottleInteractionMapper interactionMapper;
    private final WishMapper wishMapper;
    private final UserStatService userStatService;
    private final EncounterEventProducer encounterEventProducer;

    @Override
    @Transactional
    public DriftBottleVO throwBottle(Long userId, ThrowBottleRequest request) {
        boolean hasText = request.content() != null && !request.content().isBlank();
        boolean hasWish = request.wishId() != null;
        if (!hasText && !hasWish) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "请填写漂流瓶文字或选择一个心愿");
        }
        if (hasText && hasWish) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "自由文字与关联心愿只能二选一");
        }

        DriftBottle bottle = new DriftBottle();
        bottle.setThrowerUserId(userId);
        bottle.setStatus(DriftBottleStatus.FLOATING);
        bottle.setThrownAt(LocalDateTime.now(ZoneId.of("UTC")));

        if (hasWish) {
            Wish wish = wishMapper.selectById(request.wishId());
            if (wish == null || !wish.getUserId().equals(userId)
                    || wish.getVisibility() != WishVisibility.PUBLIC
                    || wish.getStatus() != WishStatus.ACTIVE
                    || wish.getAuditStatus() != AuditStatus.APPROVED
                    || !Boolean.TRUE.equals(wish.getIsVisible())) {
                throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "只能关联你自己的公开进行中心愿");
            }
            bottle.setWishId(wish.getId());
            bottle.setWishTitle(wish.getTitle());
            bottle.setWishTags(WishJsonUtils.stringifyList(WishJsonUtils.parseStringList(wish.getTags())));
        } else {
            bottle.setContent(request.content().trim());
        }

        bottleMapper.insert(bottle);
        return toVo(bottle, "THROWN");
    }

    @Override
    public DriftBottleVO fishBottle(Long userId) {
        for (int attempt = 0; attempt < FISH_ATTEMPTS; attempt++) {
            DriftBottle candidate = bottleMapper.selectList(new LambdaQueryWrapper<DriftBottle>()
                    .eq(DriftBottle::getStatus, DriftBottleStatus.FLOATING)
                    .ne(DriftBottle::getThrowerUserId, userId)
                    .last("ORDER BY RAND() LIMIT 1"))
                    .stream().findFirst().orElse(null);
            if (candidate == null) {
                return null;
            }
            LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
            int updated = bottleMapper.update(null, new LambdaUpdateWrapper<DriftBottle>()
                    .set(DriftBottle::getStatus, DriftBottleStatus.PICKED)
                    .set(DriftBottle::getPickerUserId, userId)
                    .set(DriftBottle::getPickedAt, now)
                    .eq(DriftBottle::getId, candidate.getId())
                    .eq(DriftBottle::getStatus, DriftBottleStatus.FLOATING));
            if (updated > 0) {
                candidate.setStatus(DriftBottleStatus.PICKED);
                candidate.setPickerUserId(userId);
                candidate.setPickedAt(now);
                return toVo(candidate, "PICKED");
            }
            // 被他人并发捞走：重试
        }
        return null;
    }

    @Override
    public List<DriftBottleVO> listMine(Long userId) {
        List<DriftBottle> thrown = bottleMapper.selectList(new LambdaQueryWrapper<DriftBottle>()
                .eq(DriftBottle::getThrowerUserId, userId)
                .orderByDesc(DriftBottle::getId)
                .last("LIMIT 100"));
        List<DriftBottle> picked = bottleMapper.selectList(new LambdaQueryWrapper<DriftBottle>()
                .eq(DriftBottle::getPickerUserId, userId)
                .orderByDesc(DriftBottle::getId)
                .last("LIMIT 100"));

        List<DriftBottleVO> result = new ArrayList<>();
        for (DriftBottle bottle : thrown) {
            result.add(toVo(bottle, "THROWN"));
        }
        for (DriftBottle bottle : picked) {
            result.add(toVo(bottle, "PICKED"));
        }
        result.sort(Comparator.comparing(DriftBottleVO::bottleId).reversed());
        return result;
    }

    @Override
    @Transactional
    public DriftBottleVO interact(Long userId, Long bottleId, String type) {
        DriftBottle bottle = bottleMapper.selectById(bottleId);
        if (bottle == null || !userId.equals(bottle.getPickerUserId())) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "漂流瓶不存在");
        }
        if (bottle.getWishId() == null) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "文字漂流瓶不可回应");
        }
        boolean isLight = "LIGHT".equalsIgnoreCase(type);
        if (!isLight && !"BLESS".equalsIgnoreCase(type)) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "回应类型非法");
        }

        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        DriftBottleInteraction interaction = new DriftBottleInteraction();
        interaction.setBottleId(bottleId);
        interaction.setUserId(userId);
        interaction.setType(isLight ? "LIGHT" : "BLESS");
        interaction.setPeerWishId(bottle.getWishId());
        interaction.setInteractDate(today);
        try {
            interactionMapper.insert(interaction);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(WishErrorCodes.WISH_RATE_LIMITED, "这个漂流瓶今天已经回应过啦");
        }

        if (isLight) {
            // 点亮对方心愿：扣星光 2（LIGHT_OTHER 流水）+ 对方心愿 light_count+1
            userStatService.spendStarlight(userId, LIGHT_COST, ResourceLogSource.LIGHT_OTHER, bottleId);
            wishMapper.update(null, new LambdaUpdateWrapper<Wish>()
                    .setSql("light_count = light_count + 1")
                    .eq(Wish::getId, bottle.getWishId()));
        }
        // 匿名通知投瓶人（不含捞起者身份，无法反查）
        encounterEventProducer.publishBottleInteraction(bottle.getThrowerUserId(), isLight);

        return toVo(bottle, "PICKED");
    }

    private DriftBottleVO toVo(DriftBottle bottle, String role) {
        return new DriftBottleVO(
                bottle.getId(),
                bottle.getContent(),
                bottle.getWishId(),
                bottle.getWishTitle(),
                WishJsonUtils.parseStringList(bottle.getWishTags()),
                bottle.getStatus().name(),
                role,
                bottle.getThrownAt(),
                bottle.getPickedAt());
    }
}