package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.BottleCommentRequest;
import com.cloudmart.wish.dto.ThrowBottleRequest;
import com.cloudmart.wish.entity.DriftBottle;
import com.cloudmart.wish.entity.DriftBottleComment;
import com.cloudmart.wish.entity.DriftBottleInteraction;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.enums.AuditStatus;
import com.cloudmart.wish.enums.DriftBottleStatus;
import com.cloudmart.wish.enums.ResourceLogSource;
import com.cloudmart.wish.enums.WishStatus;
import com.cloudmart.wish.enums.WishVisibility;
import com.cloudmart.wish.feign.UserFeignClient;
import com.cloudmart.wish.mq.EncounterEventProducer;
import com.cloudmart.wish.repository.DriftBottleCommentMapper;
import com.cloudmart.wish.repository.DriftBottleInteractionMapper;
import com.cloudmart.wish.repository.DriftBottleMapper;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.service.DriftBottleService;
import com.cloudmart.wish.service.UserStatService;
import com.cloudmart.wish.util.WishJsonUtils;
import com.cloudmart.wish.vo.DriftBottleCandidateWishVO;
import com.cloudmart.wish.vo.DriftBottleCommentVO;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 漂流瓶服务实现。
 *
 * <p>捞瓶并发安全：随机候选 + 条件 UPDATE（status=FLOATING → PICKED），
 * 更新未命中即被他人并发捞走，进入下一轮重试（最多 {@value #FISH_ATTEMPTS} 次）。</p>
 *
 * <p>匿名模型：投瓶默认匿名（实名时对捞起者透出身份）；评论默认匿名（实名时透出昵称头像），
 * 评论仅投瓶人与捞起人可见、可评。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DriftBottleServiceImpl implements DriftBottleService {

    private static final int LIGHT_COST = 2;
    private static final int FISH_ATTEMPTS = 3;
    private static final String ANON_NICKNAME = "匿名瓶友";
    private static final String DEFAULT_NICKNAME = "心愿旅人";

    private final DriftBottleMapper bottleMapper;
    private final DriftBottleInteractionMapper interactionMapper;
    private final DriftBottleCommentMapper commentMapper;
    private final WishMapper wishMapper;
    private final UserStatService userStatService;
    private final EncounterEventProducer encounterEventProducer;
    private final UserFeignClient userFeignClient;

    /** 用户基础信息（Feign 降级占位） */
    private record UserInfo(Long id, String nickname, String avatar) {
    }

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
        // 投瓶默认匿名；实名时捞起者可见投瓶人身份
        bottle.setIsAnonymous(!Boolean.FALSE.equals(request.isAnonymous()));

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
        return toVo(bottle, "THROWN", Map.of(), fetchUserInfo(bottleThrowerIds(List.of(bottle))));
    }

    @Override
    public List<DriftBottleCandidateWishVO> listCandidateWishes(Long userId) {
        List<Wish> wishes = wishMapper.selectList(new LambdaQueryWrapper<Wish>()
                .eq(Wish::getUserId, userId)
                .eq(Wish::getVisibility, WishVisibility.PUBLIC)
                .eq(Wish::getStatus, WishStatus.ACTIVE)
                .eq(Wish::getAuditStatus, AuditStatus.APPROVED)
                .eq(Wish::getIsVisible, true)
                .orderByDesc(Wish::getId)
                .last("LIMIT 20"));
        return wishes.stream()
                .map(w -> new DriftBottleCandidateWishVO(
                        w.getId(), w.getTitle(), WishJsonUtils.parseStringList(w.getTags())))
                .toList();
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
                List<DriftBottle> bottles = List.of(candidate);
                return toVo(candidate, "PICKED", countComments(bottles), fetchUserInfo(bottleThrowerIds(bottles)));
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

        List<DriftBottle> all = new ArrayList<>(thrown);
        all.addAll(picked);
        Map<Long, Long> commentCounts = countComments(all);
        Map<Long, UserInfo> userInfo = fetchUserInfo(bottleThrowerIds(all));

        List<DriftBottleVO> result = new ArrayList<>();
        for (DriftBottle bottle : thrown) {
            result.add(toVo(bottle, "THROWN", commentCounts, userInfo));
        }
        for (DriftBottle bottle : picked) {
            result.add(toVo(bottle, "PICKED", commentCounts, userInfo));
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

        List<DriftBottle> bottles = List.of(bottle);
        return toVo(bottle, "PICKED", countComments(bottles), fetchUserInfo(bottleThrowerIds(bottles)));
    }

    @Override
    public CommentPage listComments(Long userId, Long bottleId, String cursor, Integer pageSize) {
        // 作者级资源：非投瓶人/捞起人一律 404（防存在性探测）
        requireBottleViewable(userId, bottleId);
        int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 50);
        long cursorId = parseCursor(cursor);

        List<DriftBottleComment> comments = commentMapper.selectList(new LambdaQueryWrapper<DriftBottleComment>()
                .eq(DriftBottleComment::getBottleId, bottleId)
                .lt(cursorId != Long.MIN_VALUE, DriftBottleComment::getId, cursorId)
                .orderByDesc(DriftBottleComment::getId)
                .last("LIMIT " + (size + 1)));
        boolean hasMore = comments.size() > size;
        List<DriftBottleComment> page = hasMore ? comments.subList(0, size) : comments;
        String nextCursor = hasMore && !page.isEmpty()
                ? String.valueOf(page.get(page.size() - 1).getId()) : null;
        return new CommentPage(toCommentVos(page), nextCursor, hasMore);
    }

    @Override
    @Transactional
    public DriftBottleCommentVO addComment(Long userId, Long bottleId, BottleCommentRequest request) {
        // 作者级资源：非投瓶人/捞起人一律 404（防存在性探测）
        requireBottleViewable(userId, bottleId);

        DriftBottleComment parent = null;
        if (request.parentId() != null) {
            parent = commentMapper.selectById(request.parentId());
            if (parent == null || !bottleId.equals(parent.getBottleId())) {
                throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "被回复的评论不存在");
            }
        }

        DriftBottleComment comment = new DriftBottleComment();
        comment.setBottleId(bottleId);
        comment.setUserId(userId);
        comment.setParentId(request.parentId());
        // 被回复人由父评论推导，防伪造
        comment.setReplyToUserId(parent == null ? null : parent.getUserId());
        comment.setContent(request.content().trim());
        // 评论默认匿名，可切换实名
        comment.setIsAnonymous(!Boolean.FALSE.equals(request.isAnonymous()));
        commentMapper.insert(comment);

        Set<Long> needUserIds = new java.util.HashSet<>();
        if (!Boolean.TRUE.equals(comment.getIsAnonymous())) {
            needUserIds.add(userId);
        }
        if (parent != null && !Boolean.TRUE.equals(parent.getIsAnonymous())) {
            needUserIds.add(parent.getUserId());
        }
        Map<Long, UserInfo> userInfo = fetchUserInfo(needUserIds);
        return toCommentVo(comment, parent, userInfo);
    }

    // ==================== 内部工具 ====================

    /** 校验漂流瓶对当前用户可见（投瓶人或捞起人），否则 404 防探测 */
    private DriftBottle requireBottleViewable(Long userId, Long bottleId) {
        DriftBottle bottle = bottleMapper.selectById(bottleId);
        if (bottle == null || !(userId.equals(bottle.getThrowerUserId())
                || userId.equals(bottle.getPickerUserId()))) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "漂流瓶不存在");
        }
        return bottle;
    }

    /** 统计每瓶评论数（含回复）；空列表直接返回空 Map，避免无效 SQL */
    private Map<Long, Long> countComments(List<DriftBottle> bottles) {
        if (bottles.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = bottles.stream().map(DriftBottle::getId).toList();
        List<Map<String, Object>> rows = commentMapper.selectMaps(new QueryWrapper<DriftBottleComment>()
                .select("bottle_id", "COUNT(*) AS cnt")
                .in("bottle_id", ids)
                .groupBy("bottle_id"));
        Map<Long, Long> counts = new HashMap<>();
        for (Map<String, Object> row : rows) {
            counts.put(((Number) row.get("bottle_id")).longValue(),
                    ((Number) row.get("cnt")).longValue());
        }
        return counts;
    }

    /** 实名投瓶时透出的投瓶人用户 ID 集合 */
    private Set<Long> bottleThrowerIds(List<DriftBottle> bottles) {
        return bottles.stream()
                .filter(b -> Boolean.FALSE.equals(b.getIsAnonymous()))
                .map(DriftBottle::getThrowerUserId)
                .collect(Collectors.toSet());
    }

    /** 批量获取用户昵称头像（Feign 失败降级空 Map，前端按占位展示，Fail Open） */
    private Map<Long, UserInfo> fetchUserInfo(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        try {
            var response = userFeignClient.batchGetUsers(new ArrayList<>(userIds));
            if (response.success() && response.data() != null) {
                return response.data().stream().collect(Collectors.toMap(
                        m -> ((Number) m.get("id")).longValue(),
                        m -> new UserInfo(
                                ((Number) m.get("id")).longValue(),
                                (String) m.getOrDefault("nickname", DEFAULT_NICKNAME),
                                (String) m.getOrDefault("avatar", ""))));
            }
        } catch (Exception e) {
            log.warn("批量获取漂流瓶用户信息失败，降级为占位数据: {}", e.getMessage());
        }
        return Map.of();
    }

    private DriftBottleVO toVo(DriftBottle bottle, String role,
                               Map<Long, Long> commentCounts, Map<Long, UserInfo> userInfo) {
        boolean anonymous = !Boolean.FALSE.equals(bottle.getIsAnonymous());
        Long throwerId = anonymous ? null : bottle.getThrowerUserId();
        UserInfo info = throwerId == null ? null : userInfo.get(throwerId);
        return new DriftBottleVO(
                bottle.getId(),
                bottle.getContent(),
                bottle.getWishId(),
                bottle.getWishTitle(),
                WishJsonUtils.parseStringList(bottle.getWishTags()),
                bottle.getStatus().name(),
                role,
                bottle.getThrownAt(),
                bottle.getPickedAt(),
                anonymous,
                throwerId,
                info == null ? null : info.nickname(),
                info == null ? null : info.avatar(),
                commentCounts.getOrDefault(bottle.getId(), 0L));
    }

    /** 批量转评论 VO：匿名评论隐藏身份；被回复人昵称遵循父评论匿名状态 */
    private List<DriftBottleCommentVO> toCommentVos(List<DriftBottleComment> comments) {
        if (comments.isEmpty()) {
            return List.of();
        }
        // 父评论可能不在当前页：补查（一次批量），并收集需展示真实身份的用户
        Set<Long> parentIds = comments.stream()
                .map(DriftBottleComment::getParentId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, DriftBottleComment> parents = new HashMap<>();
        if (!parentIds.isEmpty()) {
            commentMapper.selectBatchIds(parentIds)
                    .forEach(p -> parents.put(p.getId(), p));
        }
        Set<Long> needUserIds = new java.util.HashSet<>();
        comments.forEach(c -> {
            if (!Boolean.TRUE.equals(c.getIsAnonymous())) {
                needUserIds.add(c.getUserId());
            }
            DriftBottleComment parent = parents.get(c.getParentId());
            if (parent != null && !Boolean.TRUE.equals(parent.getIsAnonymous())) {
                needUserIds.add(parent.getUserId());
            }
        });
        Map<Long, UserInfo> userInfo = fetchUserInfo(needUserIds);
        return comments.stream()
                .map(c -> toCommentVo(c, parents.get(c.getParentId()), userInfo))
                .toList();
    }

    private DriftBottleCommentVO toCommentVo(DriftBottleComment comment,
                                             DriftBottleComment parent, Map<Long, UserInfo> userInfo) {
        boolean anonymous = !Boolean.FALSE.equals(comment.getIsAnonymous());
        String nickname;
        String avatar;
        if (anonymous) {
            nickname = ANON_NICKNAME;
            avatar = null;
        } else {
            UserInfo info = userInfo.get(comment.getUserId());
            nickname = info == null ? DEFAULT_NICKNAME : info.nickname();
            avatar = info == null ? "" : info.avatar();
        }
        String replyToNickname = null;
        if (parent != null) {
            boolean parentAnon = !Boolean.FALSE.equals(parent.getIsAnonymous());
            if (parentAnon) {
                replyToNickname = ANON_NICKNAME;
            } else {
                UserInfo info = userInfo.get(parent.getUserId());
                replyToNickname = info == null ? DEFAULT_NICKNAME : info.nickname();
            }
        }
        return new DriftBottleCommentVO(
                comment.getId(),
                comment.getBottleId(),
                anonymous ? null : comment.getUserId(),
                nickname,
                avatar,
                comment.getParentId(),
                replyToNickname,
                comment.getContent(),
                anonymous,
                comment.getCreatedAt());
    }

    /** 游标解析：空/非法游标 → null 表示第一页（camelCase 与心愿评论一致口径） */
    private long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return Long.MIN_VALUE;
        }
        try {
            return Long.parseLong(cursor);
        } catch (NumberFormatException e) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "无效的游标格式");
        }
    }
}