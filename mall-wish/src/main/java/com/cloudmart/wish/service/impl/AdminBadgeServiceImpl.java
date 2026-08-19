package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.AdminCreateBadgeRequest;
import com.cloudmart.wish.dto.AdminUpdateBadgeRequest;
import com.cloudmart.wish.entity.WishBadge;
import com.cloudmart.wish.repository.WishBadgeMapper;
import com.cloudmart.wish.service.AdminBadgeService;
import com.cloudmart.wish.vo.AdminBadgeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管理端徽章服务实现。
 *
 * <p>单行写操作无需 @Transactional（Mapper 自带单条原子语义）；
 * code 唯一冲突由 uk_badge_code 唯一索引兜底（先查友好提示 + DuplicateKey 并发兜底）。</p>
 */
@Service
@RequiredArgsConstructor
public class AdminBadgeServiceImpl implements AdminBadgeService {

    private final WishBadgeMapper wishBadgeMapper;

    @Override
    public List<AdminBadgeVO> listBadges() {
        return wishBadgeMapper.selectList(
                        new LambdaQueryWrapper<WishBadge>().orderByAsc(WishBadge::getId))
                .stream()
                .map(AdminBadgeServiceImpl::toVO)
                .toList();
    }

    @Override
    public AdminBadgeVO createBadge(AdminCreateBadgeRequest request) {
        String conditionError = BadgeConditionParser.validate(request.getCondition());
        if (conditionError != null) {
            throw new BusinessException(WishErrorCodes.BADGE_CONDITION_INVALID, conditionError);
        }
        if (wishBadgeMapper.selectCount(new LambdaQueryWrapper<WishBadge>()
                .eq(WishBadge::getCode, request.getCode())) > 0) {
            throw new BusinessException(WishErrorCodes.BADGE_CODE_DUPLICATED,
                    "徽章编码已存在: " + request.getCode());
        }
        WishBadge badge = new WishBadge();
        badge.setCode(request.getCode());
        badge.setName(request.getName());
        badge.setIcon(request.getIcon() == null ? "" : request.getIcon());
        badge.setRarity(request.getRarity());
        badge.setCondition(request.getCondition());
        badge.setIsActive(true);
        try {
            wishBadgeMapper.insert(badge);
        } catch (DuplicateKeyException e) {
            // 并发新增同 code：唯一索引兜底
            throw new BusinessException(WishErrorCodes.BADGE_CODE_DUPLICATED,
                    "徽章编码已存在: " + request.getCode());
        }
        return toVO(badge);
    }

    @Override
    public AdminBadgeVO updateBadge(Long badgeId, AdminUpdateBadgeRequest request) {
        String conditionError = BadgeConditionParser.validate(request.getCondition());
        if (conditionError != null) {
            throw new BusinessException(WishErrorCodes.BADGE_CONDITION_INVALID, conditionError);
        }
        WishBadge badge = requireBadge(badgeId);
        badge.setName(request.getName());
        badge.setIcon(request.getIcon() == null ? "" : request.getIcon());
        badge.setRarity(request.getRarity());
        badge.setCondition(request.getCondition());
        wishBadgeMapper.updateById(badge);
        return toVO(badge);
    }

    @Override
    public AdminBadgeVO updateBadgeStatus(Long badgeId, boolean active) {
        WishBadge badge = requireBadge(badgeId);
        badge.setIsActive(active);
        wishBadgeMapper.updateById(badge);
        return toVO(badge);
    }

    private WishBadge requireBadge(Long badgeId) {
        WishBadge badge = wishBadgeMapper.selectById(badgeId);
        if (badge == null) {
            throw new BusinessException(WishErrorCodes.BADGE_NOT_FOUND, "徽章不存在");
        }
        return badge;
    }

    private static AdminBadgeVO toVO(WishBadge badge) {
        AdminBadgeVO vo = new AdminBadgeVO();
        vo.setId(badge.getId());
        vo.setCode(badge.getCode());
        vo.setName(badge.getName());
        vo.setIcon(badge.getIcon());
        vo.setRarity(badge.getRarity());
        vo.setIsActive(Boolean.TRUE.equals(badge.getIsActive()));
        vo.setCondition(badge.getCondition());
        vo.setCreatedAt(badge.getCreatedAt());
        vo.setUpdatedAt(badge.getUpdatedAt());
        return vo;
    }
}
