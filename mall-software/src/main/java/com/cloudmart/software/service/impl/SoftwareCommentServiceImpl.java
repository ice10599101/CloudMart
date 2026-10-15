package com.cloudmart.software.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.async.EventEnvelope;
import com.cloudmart.common.async.outbox.OutboxService;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.dto.software.InteractionContextRequest;
import com.cloudmart.software.dto.software.SoftwareReviewOutcomeVO;
import com.cloudmart.software.dto.software.SoftwareReviewRequest;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareComment;
import com.cloudmart.software.entity.SoftwareCommentLike;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.enums.SoftwareCommentStatus;
import com.cloudmart.software.feign.CommunitySupportFeignClient;
import com.cloudmart.software.feign.UserSupportFeignClient;
import com.cloudmart.software.mq.SoftwareEvents;
import com.cloudmart.software.policy.SoftwareAccessPolicy;
import com.cloudmart.software.repository.SoftwareCommentLikeMapper;
import com.cloudmart.software.repository.SoftwareCommentMapper;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.service.SoftwareCommentService;
import com.cloudmart.software.service.SoftwareOperationService;
import com.cloudmart.software.service.SoftwareRateLimiter;
import com.cloudmart.software.vo.software.SoftwareCommentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 评论实现（方案 4.4/6.8/9.6）。
 *
 * <p>关键规则：</p>
 * <ul>
 *   <li>创建两段式（9.7）：远程阶段（账号可用 + 双向拉黑 + 机审绑定结果）在事务外，
 *       TransactionTemplate 短事务落库；VISIBLE 同事务计数+1；PENDING 仅本人可见
 *       "待审核"；三级违规 CONTENT_REJECTED 拒绝发布；</li>
 *   <li>回复目标必须本软件同根楼，replyToUserId 服务端推导（A15）；</li>
 *   <li>列表隐私过滤：双向拉黑命中者内容不返回（过滤后可小于 pageSize，hasMore 以
 *       原始候选分页为准）；DELETED 根楼仅有可见回复时占位且不回传原文；HIDDEN
 *       不回传原文；拉黑查询失败 503，绝不退回"游客全量"；</li>
 *   <li>回复预览批量装载（一次 IN 查询），预览 3 条 + replyHasMore；</li>
 *   <li>点赞唯一键防重、计数不为负。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoftwareCommentServiceImpl implements SoftwareCommentService {

    private static final int REPLY_PREVIEW_COUNT = 3;

    private final SoftwareAccessPolicy accessPolicy;
    private final SoftwareMapper softwareMapper;
    private final SoftwareCommentMapper commentMapper;
    private final SoftwareCommentLikeMapper commentLikeMapper;
    private final CommunitySupportFeignClient communitySupportFeignClient;
    private final UserSupportFeignClient userSupportFeignClient;
    private final SoftwareOperationService operationService;
    private final SoftwareRateLimiter rateLimiter;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    // ------------------------------------------------------------------
    // 创建（幂等；两段式 9.7）
    // ------------------------------------------------------------------

    @Override
    public SoftwareCommentVO create(Long userId, Long softwareId, String idempotencyKey,
                                    String content, Long parentId, Long replyToCommentId) {
        SoftwareRevision published = accessPolicy.visiblePublishedRevision(softwareId);
        if (published == null) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
        }
        if (content == null || content.isBlank() || content.length() > 1000) {
            throw new BusinessException("SOFTWARE_COMMENT_INVALID", "评论正文须为 1~1000 字");
        }
        if (replyToCommentId != null && parentId == null) {
            throw new BusinessException("SOFTWARE_COMMENT_INVALID", "楼中楼回复必须携带根评论 ID");
        }
        // 11.2 限频：单用户每分钟评论上限（Redis 降级放行，正确性由 operation 幂等兜底）
        if (!rateLimiter.allowComment(userId)) {
            throw new BusinessException("SOFTWARE_RATE_LIMITED", "评论过于频繁，请稍后再试");
        }

        // 远程阶段（事务外，9.7）：账号可用 + 双向拉黑 + 机审绑定结果
        requireAccountAvailable(userId);
        if (replyToCommentId != null) {
            resolveReplyTarget(softwareId, parentId, replyToCommentId);
        }
        Long softwareOwnerId = softwareMapper.selectById(softwareId).getOwnerId();
        Long peerUserId = replyToCommentId != null
                ? Objects.requireNonNull(commentMapper.selectById(replyToCommentId)).getUserId()
                : softwareOwnerId;
        requireInteractionAllowed(userId, peerUserId);
        SoftwareReviewOutcomeVO outcome = communitySupportFeignClient.review(
                new SoftwareReviewRequest(idempotencyKey, sha256(content),
                        List.of(content), 0)).data();
        if (outcome == null || !outcome.approved()) {
            // 三级违规：失败不得发布（9.6）
            throw new BusinessException("CONTENT_REJECTED",
                    outcome == null ? "内容审核不可用" : outcome.reason());
        }

        CreatedComment created = transactionTemplate.execute(status -> operationService.execute(
                "USER", userId, SoftwareOperationService.OP_CREATE_COMMENT,
                idempotencyKey, Map.of("softwareId", softwareId, "content", content,
                        "parentId", String.valueOf(parentId),
                        "replyToCommentId", String.valueOf(replyToCommentId)),
                softwareId,
                () -> doCreate(userId, softwareId, content, parentId, replyToCommentId, outcome),
                json -> objectMapper.readValue(json, CreatedComment.class)));
        return toSelfVo(Objects.requireNonNull(commentMapper.selectById(created.commentId())));
    }

    /** 创建结果（只存 ID/状态，6.10） */
    private record CreatedComment(Long commentId, String status) {
    }

    /** 短事务内落库（仅由 TransactionTemplate 调用；VISIBLE 同事务计数+1+事件） */
    private CreatedComment doCreate(Long userId, Long softwareId, String content,
                                    Long parentId, Long replyToCommentId,
                                    SoftwareReviewOutcomeVO outcome) {
        Long replyToUserId = null;
        if (replyToCommentId != null) {
            replyToUserId = Objects.requireNonNull(
                    commentMapper.selectById(replyToCommentId)).getUserId();
        }
        boolean manualReview = outcome.needsManualReview();
        SoftwareComment comment = new SoftwareComment();
        comment.setSoftwareId(softwareId);
        comment.setUserId(userId);
        comment.setParentId(parentId);
        comment.setReplyToCommentId(replyToCommentId);
        comment.setReplyToUserId(replyToUserId);
        comment.setContent(content);
        comment.setStatus(manualReview ? SoftwareCommentStatus.PENDING : SoftwareCommentStatus.VISIBLE);
        comment.setLikeCount(0);
        comment.setPinned(false);
        comment.setLockVersion(0);
        commentMapper.insert(comment);

        if (!manualReview) {
            softwareMapper.incrementCommentCount(softwareId, 1);
            publishVisibleEvent(comment);
        }
        return new CreatedComment(comment.getId(), comment.getStatus().name());
    }

    /** 回复目标：必须本软件同根楼（A15）；返回被回复人供拉黑与通知推导 */
    private void resolveReplyTarget(Long softwareId, Long parentId, Long replyToCommentId) {
        SoftwareComment root = commentMapper.selectById(parentId);
        if (root == null || !softwareId.equals(root.getSoftwareId()) || root.getParentId() != null) {
            throw new BusinessException("SOFTWARE_COMMENT_INVALID", "根评论不存在或不属于该软件");
        }
        SoftwareComment target = commentMapper.selectById(replyToCommentId);
        if (target == null || !softwareId.equals(target.getSoftwareId())) {
            throw new BusinessException("SOFTWARE_COMMENT_INVALID", "回复目标不存在或不属于该软件");
        }
        Long targetRoot = target.getParentId() == null ? target.getId() : target.getParentId();
        if (!targetRoot.equals(parentId)) {
            throw new BusinessException("SOFTWARE_COMMENT_INVALID", "只能回复同一根楼内的评论");
        }
    }

    /** 双向拉黑：命中拒绝；查询失败 fail-closed 503（fallback 抛出） */
    private void requireInteractionAllowed(Long userId, Long peerUserId) {
        if (userId.equals(peerUserId)) {
            return;
        }
        var context = communitySupportFeignClient.interactionContext(
                new InteractionContextRequest(userId, List.of(peerUserId))).data();
        if (context == null) {
            throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "互动资格服务暂不可用");
        }
        if (!context.canInteract()) {
            throw new BusinessException("SOFTWARE_FORBIDDEN", "无法与该用户互动");
        }
    }

    private void requireAccountAvailable(Long userId) {
        var profiles = userSupportFeignClient.profiles(Map.of("userIds", List.of(userId))).data();
        if (profiles == null || profiles.isEmpty()) {
            throw new BusinessException("USER_SERVICE_UNAVAILABLE", "账号状态不可用，请稍后重试");
        }
        if (!profiles.get(0).accountAvailable()) {
            throw new BusinessException("ACCOUNT_UNAVAILABLE", "账号状态异常，无法执行该操作");
        }
    }

    // ------------------------------------------------------------------
    // 删除（本人；软删占位）
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public void delete(Long userId, Long softwareId, Long commentId) {
        requireHostVisible(softwareId);
        SoftwareComment comment = commentMapper.selectById(commentId);
        if (comment == null || !softwareId.equals(comment.getSoftwareId())
                || comment.getDeletedAt() != null) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "评论不存在");
        }
        if (!userId.equals(comment.getUserId())) {
            // 4.4：分享者不能删他人留言（可举报）
            throw new BusinessException("SOFTWARE_FORBIDDEN", "只能删除自己的评论");
        }
        boolean wasVisible = comment.getStatus() == SoftwareCommentStatus.VISIBLE;
        comment.setStatus(SoftwareCommentStatus.DELETED);
        comment.setDeletedAt(LocalDateTime.now(clock));
        comment.setLockVersion(comment.getLockVersion() + 1);
        commentMapper.updateById(comment);
        if (wasVisible) {
            softwareMapper.incrementCommentCount(softwareId, -1);
        }
    }

    // ------------------------------------------------------------------
    // 根楼列表（隐私过滤 + 批量回复预览）
    // ------------------------------------------------------------------

    @Override
    public Page<SoftwareCommentVO> pageRoots(Long softwareId, Long viewerId, int page, int size) {
        requireHostVisible(softwareId);
        Page<SoftwareComment> candidatePage = commentMapper.selectPage(
                Page.of(Math.max(page, 1), Math.min(Math.max(size, 1), 50)),
                rootCandidateWrapper(softwareId, viewerId));

        List<SoftwareComment> candidates = candidatePage.getRecords();
        Set<Long> blockedAuthors = resolveBlockedAuthors(viewerId, candidates.stream()
                .map(SoftwareComment::getUserId).toList());

        // 批量装载候选根楼的可见回复（一次 IN 查询），拉黑过滤后取预览与 hasMore
        List<Long> rootIds = candidates.stream().map(SoftwareComment::getId).toList();
        Map<Long, List<SoftwareComment>> visibleRepliesByRoot = rootIds.isEmpty() ? Map.of()
                : commentMapper.selectList(new LambdaQueryWrapper<SoftwareComment>()
                                .in(SoftwareComment::getParentId, rootIds)
                                .eq(SoftwareComment::getStatus, SoftwareCommentStatus.VISIBLE)
                                .orderByAsc(SoftwareComment::getCreatedAt))
                        .stream()
                        .filter(reply -> !blockedAuthors.contains(reply.getUserId()))
                        .collect(Collectors.groupingBy(SoftwareComment::getParentId));

        Page<SoftwareCommentVO> result = new Page<>(candidatePage.getCurrent(),
                candidatePage.getSize(), candidatePage.getTotal());
        result.setRecords(candidates.stream()
                .map(comment -> toRootVo(comment, viewerId, blockedAuthors, visibleRepliesByRoot))
                .filter(Objects::nonNull)
                .toList());
        return result;
    }

    /** 候选根楼：可见 + 已删占位 + 本人待审（游客不含待审） */
    private LambdaQueryWrapper<SoftwareComment> rootCandidateWrapper(Long softwareId, Long viewerId) {
        LambdaQueryWrapper<SoftwareComment> wrapper = new LambdaQueryWrapper<SoftwareComment>()
                .eq(SoftwareComment::getSoftwareId, softwareId)
                .isNull(SoftwareComment::getParentId);
        if (viewerId == null) {
            wrapper.in(SoftwareComment::getStatus,
                    SoftwareCommentStatus.VISIBLE, SoftwareCommentStatus.DELETED);
        } else {
            wrapper.and(w -> w
                    .in(SoftwareComment::getStatus,
                            SoftwareCommentStatus.VISIBLE, SoftwareCommentStatus.DELETED)
                    .or(inner -> inner.eq(SoftwareComment::getStatus, SoftwareCommentStatus.PENDING)
                            .eq(SoftwareComment::getUserId, viewerId)));
        }
        return wrapper.orderByDesc(SoftwareComment::getCreatedAt)
                .orderByDesc(SoftwareComment::getId);
    }

    private SoftwareCommentVO toRootVo(SoftwareComment comment, Long viewerId,
                                       Set<Long> blockedAuthors,
                                       Map<Long, List<SoftwareComment>> repliesByRoot) {
        if (blockedAuthors.contains(comment.getUserId())) {
            return null;
        }
        String status = resolveDisplayStatus(comment, viewerId);
        List<SoftwareComment> visibleReplies =
                repliesByRoot.getOrDefault(comment.getId(), List.of());
        boolean hasMore = visibleReplies.size() > REPLY_PREVIEW_COUNT;
        // DELETED 占位：无可见回复则整体不返回（6.8）
        if (comment.getStatus() == SoftwareCommentStatus.DELETED && visibleReplies.isEmpty()) {
            return null;
        }
        List<SoftwareCommentVO> previews = visibleReplies.stream()
                .limit(REPLY_PREVIEW_COUNT)
                .map(reply -> toVo(reply, viewerId))
                .toList();
        return new SoftwareCommentVO(comment.getId(), null, comment.getUserId(),
                null, null, displayContent(comment, status, isSelf(comment, viewerId)),
                status, comment.getLikeCount(),
                viewerId == null ? null : likedByMe(comment.getId(), viewerId),
                comment.getCreatedAt(), previews, hasMore);
    }

    private SoftwareCommentVO toVo(SoftwareComment comment, Long viewerId) {
        String status = resolveDisplayStatus(comment, viewerId);
        return new SoftwareCommentVO(comment.getId(), comment.getParentId(), comment.getUserId(),
                null, null, displayContent(comment, status, isSelf(comment, viewerId)),
                status, comment.getLikeCount(),
                viewerId == null ? null : likedByMe(comment.getId(), viewerId),
                comment.getCreatedAt(), null, null);
    }

    /** 本人创建的待审评论展示为 SELF_PENDING（"待审核"） */
    private String resolveDisplayStatus(SoftwareComment comment, Long viewerId) {
        if (comment.getStatus() == SoftwareCommentStatus.PENDING
                && viewerId != null && viewerId.equals(comment.getUserId())) {
            return "SELF_PENDING";
        }
        return comment.getStatus().name();
    }

    /** DELETED 占位/HIDDEN 不回传原文；本人待审可见原文 */
    private String displayContent(SoftwareComment comment, String status, boolean self) {
        if ("DELETED".equals(status) || "HIDDEN".equals(status)) {
            return null;
        }
        if ("PENDING".equals(status)) {
            return self ? comment.getContent() : null;
        }
        return comment.getContent();
    }

    private boolean isSelf(SoftwareComment comment, Long viewerId) {
        return viewerId != null && viewerId.equals(comment.getUserId());
    }

    // ------------------------------------------------------------------
    // 楼中楼分页（升序；继承宿主可见性与隐私过滤）
    // ------------------------------------------------------------------

    @Override
    public Page<SoftwareCommentVO> pageReplies(Long softwareId, Long rootId, Long viewerId,
                                               int page, int size) {
        requireHostVisible(softwareId);
        SoftwareComment root = commentMapper.selectById(rootId);
        if (root == null || !softwareId.equals(root.getSoftwareId()) || root.getParentId() != null) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "评论不存在");
        }
        Page<SoftwareComment> candidatePage = commentMapper.selectPage(
                Page.of(Math.max(page, 1), Math.min(Math.max(size, 1), 50)),
                new LambdaQueryWrapper<SoftwareComment>()
                        .eq(SoftwareComment::getParentId, rootId)
                        .eq(SoftwareComment::getStatus, SoftwareCommentStatus.VISIBLE)
                        .orderByAsc(SoftwareComment::getCreatedAt));
        Set<Long> blockedAuthors = resolveBlockedAuthors(viewerId, candidatePage.getRecords()
                .stream().map(SoftwareComment::getUserId).toList());
        Page<SoftwareCommentVO> result = new Page<>(candidatePage.getCurrent(),
                candidatePage.getSize(), candidatePage.getTotal());
        result.setRecords(candidatePage.getRecords().stream()
                .map(reply -> toVo(reply, viewerId))
                .toList());
        return result;
    }

    /**
     * 双向拉黑过滤集合（9.6）：候选作者批量查询；查询失败由 fallback 抛 503——
     * 绝不退回"游客全量"。
     */
    private Set<Long> resolveBlockedAuthors(Long viewerId, List<Long> authorIds) {
        if (viewerId == null || authorIds.isEmpty()) {
            return Set.of();
        }
        List<Long> distinctPeers = authorIds.stream().filter(id -> !id.equals(viewerId))
                .distinct().toList();
        if (distinctPeers.isEmpty()) {
            return Set.of();
        }
        var context = communitySupportFeignClient.interactionContext(
                new InteractionContextRequest(viewerId, distinctPeers)).data();
        if (context == null) {
            throw new BusinessException("COMMUNITY_SERVICE_UNAVAILABLE", "互动资格服务暂不可用");
        }
        return new HashSet<>(context.blockedPeerIds());
    }

    private void requireHostVisible(Long softwareId) {
        if (accessPolicy.visiblePublishedRevision(softwareId) == null) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
        }
    }

    private Boolean likedByMe(Long commentId, Long viewerId) {
        return commentLikeMapper.selectCount(new LambdaQueryWrapper<SoftwareCommentLike>()
                .eq(SoftwareCommentLike::getCommentId, commentId)
                .eq(SoftwareCommentLike::getUserId, viewerId)) > 0;
    }

    /** 创建响应：本人视角单条（昵称由前端本地上下文补齐，避免重复远程调用） */
    private SoftwareCommentVO toSelfVo(SoftwareComment comment) {
        return new SoftwareCommentVO(comment.getId(), comment.getParentId(), comment.getUserId(),
                null, null, comment.getContent(), resolveDisplayStatus(comment, comment.getUserId()),
                comment.getLikeCount(), false, comment.getCreatedAt(), null, null);
    }

    // ------------------------------------------------------------------
    // 点赞（幂等唯一关系）
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public void like(Long userId, Long softwareId, Long commentId, boolean like) {
        requireHostVisible(softwareId);
        SoftwareComment comment = commentMapper.selectById(commentId);
        if (comment == null || !softwareId.equals(comment.getSoftwareId())
                || comment.getStatus() != SoftwareCommentStatus.VISIBLE) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "评论不存在");
        }
        if (like) {
            SoftwareCommentLike relation = new SoftwareCommentLike();
            relation.setCommentId(commentId);
            relation.setUserId(userId);
            try {
                commentLikeMapper.insert(relation);
                commentMapper.incrementLikeCount(commentId);
            } catch (DuplicateKeyException e) {
                // 幂等：重复 PUT 无副作用
            }
        } else {
            int removed = commentLikeMapper.delete(new LambdaQueryWrapper<SoftwareCommentLike>()
                    .eq(SoftwareCommentLike::getCommentId, commentId)
                    .eq(SoftwareCommentLike::getUserId, userId));
            if (removed > 0) {
                commentMapper.decrementLikeCount(commentId);
            }
        }
    }

    // ------------------------------------------------------------------
    // 可见事件（S10 消费投递；PENDING→VISIBLE 的事件在 S09 人工通过时写）
    // ------------------------------------------------------------------

    private void publishVisibleEvent(SoftwareComment comment) {
        boolean isReply = comment.getParentId() != null;
        Long softwareOwnerId = softwareMapper.selectById(comment.getSoftwareId()).getOwnerId();
        Long recipientId = isReply && comment.getReplyToUserId() != null
                ? comment.getReplyToUserId() : softwareOwnerId;
        if (recipientId.equals(comment.getUserId())) {
            return; // 不自通知
        }
        String eventType = isReply ? SoftwareEvents.TAG_REPLY_VISIBLE
                : SoftwareEvents.TAG_COMMENT_VISIBLE;
        outboxService.record(EventEnvelope.of(eventType, 1,
                String.valueOf(comment.getSoftwareId()), comment.getId(), null,
                objectMapper.writeValueAsString(Map.of(
                        "softwareId", String.valueOf(comment.getSoftwareId()),
                        "commentId", String.valueOf(comment.getId()),
                        "recipientId", String.valueOf(recipientId),
                        "authorId", String.valueOf(comment.getUserId())))));
    }

    private String sha256(String text) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
