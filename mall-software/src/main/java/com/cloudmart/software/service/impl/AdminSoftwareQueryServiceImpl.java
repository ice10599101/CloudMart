package com.cloudmart.software.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.async.EventEnvelope;
import com.cloudmart.common.async.outbox.OutboxService;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareAuditLog;
import com.cloudmart.software.entity.SoftwareComment;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.enums.SoftwareCommentStatus;
import com.cloudmart.software.enums.SoftwareReviewStatus;
import com.cloudmart.software.mq.SoftwareEvents;
import com.cloudmart.software.repository.SoftwareAuditLogMapper;
import com.cloudmart.software.repository.SoftwareCommentMapper;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareRevisionMapper;
import com.cloudmart.software.service.AdminSoftwareQueryService;
import com.cloudmart.software.service.SoftwareOperationService;
import com.cloudmart.software.service.SoftwarePublishService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 管理端查询与评论处置实现（方案 7.5/6.8/9.6）。
 * 评论处置状态机与计数同事务；operation(ADMIN) 幂等防重复处置。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSoftwareQueryServiceImpl implements AdminSoftwareQueryService {

    private static final Set<String> MODERATE_ACTIONS = Set.of("APPROVE", "HIDE", "RESTORE", "REJECT");

    private final SoftwareMapper softwareMapper;
    private final SoftwareRevisionMapper revisionMapper;
    private final SoftwareCommentMapper commentMapper;
    private final SoftwareAuditLogMapper auditLogMapper;
    private final SoftwareOperationService operationService;
    private final SoftwarePublishService softwarePublishService;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    public Page<Map<String, Object>> pendingPage(int page, int size) {
        Page<SoftwareRevision> revisionPage = revisionMapper.selectPage(
                Page.of(Math.max(page, 1), Math.min(Math.max(size, 1), 100)),
                new LambdaQueryWrapper<SoftwareRevision>()
                        .eq(SoftwareRevision::getReviewStatus, SoftwareReviewStatus.PENDING)
                        .orderByAsc(SoftwareRevision::getSubmittedAt));
        Page<Map<String, Object>> result = new Page<>(revisionPage.getCurrent(),
                revisionPage.getSize(), revisionPage.getTotal());
        result.setRecords(revisionPage.getRecords().stream()
                .map(revision -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("softwareId", String.valueOf(revision.getSoftwareId()));
                    row.put("revisionId", String.valueOf(revision.getId()));
                    row.put("revisionNo", revision.getRevisionNo());
                    row.put("submitSeq", revision.getSubmitSeq());
                    row.put("name", revision.getName());
                    row.put("summary", revision.getSummary());
                    row.put("editorId", String.valueOf(revision.getEditorId()));
                    row.put("submittedAt", revision.getSubmittedAt());
                    Software software = softwareMapper.selectById(revision.getSoftwareId());
                    row.put("lifecycle", software == null ? null : software.getLifecycle().name());
                    return row;
                })
                .toList());
        return result;
    }

    @Override
    public Map<String, Object> adminDetail(Long softwareId) {
        Software software = softwareMapper.selectById(softwareId);
        if (software == null) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
        }
        // 后台详情展示全部快照（工作优先，其次线上）——独立鉴权可读秘密字段
        Long revisionId = software.getWorkingRevisionId() != null
                ? software.getWorkingRevisionId() : software.getPublishedRevisionId();
        Map<String, Object> detail = new HashMap<>();
        detail.put("softwareId", String.valueOf(softwareId));
        detail.put("lifecycle", software.getLifecycle().name());
        detail.put("lockVersion", software.getLockVersion());
        detail.put("publishedRevisionId",
                software.getPublishedRevisionId() == null ? null
                        : String.valueOf(software.getPublishedRevisionId()));
        detail.put("workingRevisionId", software.getWorkingRevisionId() == null ? null
                : String.valueOf(software.getWorkingRevisionId()));
        detail.put("publicationSeq", software.getPublicationSeq());
        detail.put("ownerId", String.valueOf(software.getOwnerId()));
        detail.put("featuredRank", software.getFeaturedRank());
        detail.put("commentCount", software.getCommentCount());
        detail.put("favoriteCount", software.getFavoriteCount());
        detail.put("linkAccessCount", software.getLinkAccessCount());
        detail.put("ratingCount", software.getRatingCount());
        detail.put("ratingSum", software.getRatingSum());
        detail.put("publishedAt",
                software.getPublishedAt() == null ? null : String.valueOf(software.getPublishedAt()));
        detail.put("lastReleaseAt",
                software.getLastReleaseAt() == null ? null
                        : String.valueOf(software.getLastReleaseAt()));
        detail.put("createdAt",
                software.getCreatedAt() == null ? null : String.valueOf(software.getCreatedAt()));
        detail.put("updatedAt",
                software.getUpdatedAt() == null ? null : String.valueOf(software.getUpdatedAt()));
        SoftwareRevision pending = revisionMapper.selectOne(new LambdaQueryWrapper<SoftwareRevision>()
                .eq(SoftwareRevision::getSoftwareId, softwareId)
                .eq(SoftwareRevision::getReviewStatus, SoftwareReviewStatus.PENDING)
                .orderByDesc(SoftwareRevision::getId)
                .last("LIMIT 1"));
        detail.put("pendingRevisionId", pending == null ? null : String.valueOf(pending.getId()));
        detail.put("revisionId", revisionId == null ? null : String.valueOf(revisionId));
        if (revisionId != null) {
            SoftwareRevision revision = revisionMapper.selectById(revisionId);
            if (revision != null) {
                detail.put("name", revision.getName());
                detail.put("summary", revision.getSummary());
                detail.put("reviewStatus", revision.getReviewStatus().name());
                detail.put("submitSeq", revision.getSubmitSeq());
                detail.put("accessMode", revision.getAccessMode() == null ? null
                        : revision.getAccessMode().name());
                detail.put("minCommunityLevel", revision.getMinCommunityLevel());
                detail.put("accessContactId", revision.getAccessContactId() == null ? null
                        : String.valueOf(revision.getAccessContactId()));
                detail.put("accessNote", revision.getAccessNote());
                detail.put("reviewReason", revision.getReviewReason());
            }
        }
        List<SoftwareAuditLog> history = auditLogMapper.selectList(
                new LambdaQueryWrapper<SoftwareAuditLog>()
                        .eq(SoftwareAuditLog::getSoftwareId, softwareId)
                        .orderByDesc(SoftwareAuditLog::getId)
                        .last("LIMIT 20"));
        detail.put("auditTrail", history.stream()
                .map(logEntry -> Map.of(
                        "action", logEntry.getAction() == null ? "" : logEntry.getAction(),
                        "actorType", logEntry.getActorType() == null ? "" : logEntry.getActorType(),
                        "fromStatus", logEntry.getFromStatus() == null ? "" : logEntry.getFromStatus(),
                        "toStatus", logEntry.getToStatus() == null ? "" : logEntry.getToStatus(),
                        "reason", logEntry.getReason() == null ? "" : logEntry.getReason(),
                        "createdAt", String.valueOf(logEntry.getCreatedAt())))
                .toList());
        return detail;
    }

    @Override
    public Map<String, Object> reviewDetail(Long revisionId) {
        SoftwareRevision revision = revisionMapper.selectById(revisionId);
        if (revision == null) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "快照不存在");
        }
        Software software = softwareMapper.selectById(revision.getSoftwareId());
        if (software == null) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
        }
        Map<String, Object> detail = new HashMap<>();
        detail.put("softwareId", String.valueOf(software.getId()));
        detail.put("revisionId", String.valueOf(revisionId));
        detail.put("revisionNo", revision.getRevisionNo());
        detail.put("reviewStatus", revision.getReviewStatus().name());
        detail.put("submitSeq", revision.getSubmitSeq());
        detail.put("lockVersion", revision.getLockVersion());
        detail.put("editorId", String.valueOf(revision.getEditorId()));
        detail.put("lifecycle", software.getLifecycle().name());
        // 审核决策的 expectedVersion 语义是软件主体 lockVersion（requireVersion(software)）
        detail.put("softwareLockVersion", software.getLockVersion());
        detail.put("publishedRevisionId", software.getPublishedRevisionId() == null ? null
                : String.valueOf(software.getPublishedRevisionId()));
        // 目标快照（待审/已审）完整内容 + 当前线上快照；线上不存在=首次发布，前端不做对比
        detail.put("target", softwarePublishService.revisionSnapshot(
                software.getId(), revisionId));
        Long publishedId = software.getPublishedRevisionId();
        detail.put("published", publishedId == null ? null
                : softwarePublishService.revisionSnapshot(software.getId(), publishedId));
        return detail;
    }

    @Override
    public Page<Map<String, Object>> commentPage(Long softwareId, String status, int page, int size) {
        if (softwareId != null && softwareMapper.selectById(softwareId) == null) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
        }
        Page<SoftwareComment> commentPage = commentMapper.selectPage(
                Page.of(Math.max(page, 1), Math.min(Math.max(size, 1), 100)),
                new LambdaQueryWrapper<SoftwareComment>()
                        .eq(softwareId != null, SoftwareComment::getSoftwareId, softwareId)
                        .eq(status != null && !status.isBlank(), SoftwareComment::getStatus,
                                status == null ? null : SoftwareCommentStatus.valueOf(status))
                        .orderByDesc(SoftwareComment::getCreatedAt));
        Page<Map<String, Object>> result = new Page<>(commentPage.getCurrent(),
                commentPage.getSize(), commentPage.getTotal());
        result.setRecords(commentPage.getRecords().stream()
                .map(comment -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("commentId", String.valueOf(comment.getId()));
                    row.put("softwareId", String.valueOf(comment.getSoftwareId()));
                    row.put("userId", String.valueOf(comment.getUserId()));
                    // HIDDEN/DELETED 不回传原文（与前台一致，防旁路泄漏）
                    row.put("content", comment.getStatus() == SoftwareCommentStatus.VISIBLE
                            || comment.getStatus() == SoftwareCommentStatus.PENDING
                            ? comment.getContent() : null);
                    row.put("status", comment.getStatus().name());
                    row.put("likeCount", comment.getLikeCount());
                    row.put("createdAt", String.valueOf(comment.getCreatedAt()));
                    return row;
                })
                .toList());
        return result;
    }

    @Override
    @Transactional
    public boolean moderateComment(Long adminId, Long softwareId, Long commentId,
                                   String idempotencyKey, String action, String reason) {
        if (action == null || !MODERATE_ACTIONS.contains(action)) {
            throw new BusinessException("SOFTWARE_COMMENT_MODERATE_INVALID",
                    "action 须为 APPROVE/HIDE/RESTORE/REJECT");
        }
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new BusinessException("SOFTWARE_COMMENT_MODERATE_INVALID", "处置原因须为 1~500 字");
        }
        operationService.execute("ADMIN", adminId, SoftwareOperationService.OP_COMMENT_MODERATE,
                idempotencyKey, Map.of("softwareId", softwareId, "commentId", commentId,
                        "action", action, "reason", reason),
                softwareId,
                (Supplier<Void>) () -> {
                    doModerate(adminId, softwareId, commentId, action, reason);
                    return null;
                },
                json -> null);
        return true;
    }

    private void doModerate(Long adminId, Long softwareId, Long commentId, String action,
                            String reason) {
        SoftwareComment comment = commentMapper.selectById(commentId);
        if (comment == null || !softwareId.equals(comment.getSoftwareId())) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "评论不存在");
        }
        SoftwareCommentStatus from = comment.getStatus();
        SoftwareCommentStatus to;
        int countDelta = 0;
        switch (action) {
            case "APPROVE" -> {
                requireState(from, SoftwareCommentStatus.PENDING, "仅待审评论可通过");
                to = SoftwareCommentStatus.VISIBLE;
                countDelta = 1;
            }
            case "REJECT" -> {
                requireState(from, SoftwareCommentStatus.PENDING, "仅待审评论可拒绝");
                to = SoftwareCommentStatus.HIDDEN;
            }
            case "HIDE" -> {
                requireState(from, SoftwareCommentStatus.VISIBLE, "仅可见评论可隐藏");
                to = SoftwareCommentStatus.HIDDEN;
                countDelta = -1;
            }
            case "RESTORE" -> {
                requireState(from, SoftwareCommentStatus.HIDDEN, "仅隐藏评论可恢复");
                to = SoftwareCommentStatus.VISIBLE;
                countDelta = 1;
            }
            default -> throw new BusinessException("SOFTWARE_COMMENT_MODERATE_INVALID",
                    "action 非法");
        }
        comment.setStatus(to);
        comment.setLockVersion(comment.getLockVersion() + 1);
        commentMapper.updateById(comment);
        if (countDelta != 0) {
            softwareMapper.incrementCommentCount(softwareId, countDelta);
        }
        // 可见化（APPROVE/RESTORE）写可见通知事件；重复处置由 operation 幂等吸收（4.4）
        if (to == SoftwareCommentStatus.VISIBLE) {
            publishVisibleEvent(comment);
        }
        auditServiceRecord(adminId, softwareId, commentId, "COMMENT_" + action,
                from.name(), to.name(), reason);
    }

    private void publishVisibleEvent(SoftwareComment comment) {
        Long softwareOwnerId = softwareMapper.selectById(comment.getSoftwareId()).getOwnerId();
        Long recipientId = comment.getReplyToUserId() != null
                && comment.getParentId() != null ? comment.getReplyToUserId() : softwareOwnerId;
        if (recipientId.equals(comment.getUserId())) {
            return;
        }
        String eventType = comment.getParentId() != null
                ? SoftwareEvents.TAG_REPLY_VISIBLE : SoftwareEvents.TAG_COMMENT_VISIBLE;
        outboxService.record(EventEnvelope.of(eventType, 1,
                String.valueOf(comment.getSoftwareId()), comment.getId(), null,
                objectMapper.writeValueAsString(Map.of(
                        "softwareId", String.valueOf(comment.getSoftwareId()),
                        "commentId", String.valueOf(comment.getId()),
                        "recipientId", String.valueOf(recipientId),
                        "authorId", String.valueOf(comment.getUserId())))));
    }

    private void requireState(SoftwareCommentStatus actual, SoftwareCommentStatus expected,
                              String message) {
        if (actual != expected) {
            throw new BusinessException("SOFTWARE_STATE_CONFLICT", message);
        }
    }

    private void auditServiceRecord(Long adminId, Long softwareId, Long commentId,
                                    String action, String from, String to, String reason) {
        SoftwareAuditLog logEntry = new SoftwareAuditLog();
        logEntry.setSoftwareId(softwareId);
        logEntry.setRevisionId(commentId);
        logEntry.setActorId(adminId);
        logEntry.setActorType("ADMIN");
        logEntry.setAction(action);
        logEntry.setFromStatus(from);
        logEntry.setToStatus(to);
        logEntry.setReason(reason);
        logEntry.setCreatedAt(LocalDateTime.now(clock));
        auditLogMapper.insert(logEntry);
    }
}
