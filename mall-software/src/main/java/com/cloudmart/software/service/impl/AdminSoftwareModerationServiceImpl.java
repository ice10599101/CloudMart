package com.cloudmart.software.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.software.service.AdminRowEnricher;
import java.util.List;
import com.cloudmart.common.async.EventEnvelope;
import com.cloudmart.common.async.outbox.OutboxService;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.dto.software.AdminFeedbackMarkRequest;
import com.cloudmart.software.dto.software.AdminReportHandleRequest;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareAuditLog;
import com.cloudmart.software.entity.SoftwareComment;
import com.cloudmart.software.entity.SoftwareLinkFeedback;
import com.cloudmart.software.entity.SoftwareRelease;
import com.cloudmart.software.entity.SoftwareReport;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.entity.SoftwareSource;
import com.cloudmart.software.entity.SoftwareSourceState;
import com.cloudmart.software.enums.LinkFeedbackStatus;
import com.cloudmart.software.enums.SoftwareCommentStatus;
import com.cloudmart.software.enums.SoftwareLifecycle;
import com.cloudmart.software.enums.SoftwareReportStatus;
import com.cloudmart.software.enums.SoftwareReviewStatus;
import com.cloudmart.software.enums.SourceAvailability;
import com.cloudmart.software.mq.SoftwareEvents;
import com.cloudmart.software.repository.SoftwareAuditLogMapper;
import com.cloudmart.software.repository.SoftwareCommentMapper;
import com.cloudmart.software.repository.SoftwareLinkFeedbackMapper;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareReleaseMapper;
import com.cloudmart.software.repository.SoftwareReportMapper;
import com.cloudmart.software.repository.SoftwareSourceMapper;
import com.cloudmart.software.repository.SoftwareSourceStateMapper;
import com.cloudmart.software.service.AdminSoftwareModerationService;
import com.cloudmart.software.service.SoftwareOperationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 举报/反馈处置实现（方案 5.3/6.12）。
 *
 * <p>不变量：仅标记举报成立不隐式封禁，处置动作显式选择；举报状态、软件封禁/
 * 评论隐藏、审核日志与 Outbox 同一软件库事务提交；重复处置由 operation 幂等吸收；
 * RESOLVED 反馈保留 original 指纹与修复 revisionId。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSoftwareModerationServiceImpl implements AdminSoftwareModerationService {

    private static final Set<String> REPORT_STATUSES = Set.of("RESOLVED", "REJECTED");
    private static final Set<String> REPORT_ACTIONS = Set.of("NONE", "BAN_SOFTWARE", "HIDE_COMMENT");
    private static final Set<String> FEEDBACK_STATUSES = Set.of("VERIFYING", "RESOLVED", "REJECTED");

    private final SoftwareMapper softwareMapper;
    private final com.cloudmart.software.repository.SoftwareLockMapper lockMapper;
    private final SoftwareReportMapper reportMapper;
    private final SoftwareCommentMapper commentMapper;
    private final SoftwareLinkFeedbackMapper feedbackMapper;
    private final SoftwareAuditLogMapper auditLogMapper;
    private final com.cloudmart.software.feign.UserSupportFeignClient userSupportFeignClient;
    private final com.cloudmart.software.repository.SoftwareRevisionMapper revisionMapper;
    private final SoftwareReleaseMapper releaseMapper;
    private final SoftwareSourceMapper sourceMapper;
    private final SoftwareSourceStateMapper sourceStateMapper;
    private final SoftwareOperationService operationService;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    public Page<Map<String, Object>> reportPage(String status, int page, int size) {
        Page<SoftwareReport> reportPage = reportMapper.selectPage(
                Page.of(Math.max(page, 1), Math.min(Math.max(size, 1), 100)),
                new LambdaQueryWrapper<SoftwareReport>()
                        .eq(status != null && !status.isBlank(), SoftwareReport::getStatus,
                                status == null ? null : SoftwareReportStatus.valueOf(status))
                        .orderByAsc(SoftwareReport::getCreatedAt));
        Page<Map<String, Object>> result = new Page<>(reportPage.getCurrent(),
                reportPage.getSize(), reportPage.getTotal());
        List<Map<String, Object>> rows = reportPage.getRecords().stream()
                .map(report -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("reportId", String.valueOf(report.getId()));
                    row.put("reporterId", String.valueOf(report.getReporterId()));
                    row.put("softwareId", String.valueOf(report.getSoftwareId()));
                    row.put("targetType", report.getTargetType());
                    row.put("targetId", report.getTargetId() == null ? null
                            : String.valueOf(report.getTargetId()));
                    row.put("reason", report.getReason());
                    row.put("description", report.getDescription());
                    row.put("status", report.getStatus().name());
                    row.put("handleNote", report.getHandleNote());
                    row.put("createdAt", String.valueOf(report.getCreatedAt()));
                    return row;
                })
                .toList();
        AdminRowEnricher.fillUserNicknames(rows, userSupportFeignClient, "reporterId", "reporterNickname");
        AdminRowEnricher.fillSoftwareNames(rows, softwareMapper, revisionMapper, "softwareId", "softwareName");
        result.setRecords(rows);
        return result;
    }

    @Override
    @Transactional
    public boolean handleReport(Long adminId, Long reportId, String idempotencyKey,
                                  AdminReportHandleRequest request) {
        if (request.getStatus() == null || !REPORT_STATUSES.contains(request.getStatus())) {
            throw new BusinessException("SOFTWARE_REPORT_INVALID", "status 须为 RESOLVED/REJECTED");
        }
        String action = request.getAction() == null ? "NONE" : request.getAction();
        if (!REPORT_ACTIONS.contains(action)) {
            throw new BusinessException("SOFTWARE_REPORT_INVALID",
                    "action 须为 NONE/BAN_SOFTWARE/HIDE_COMMENT");
        }
        if ("REJECTED".equals(request.getStatus()) && !"NONE".equals(action)) {
            throw new BusinessException("SOFTWARE_REPORT_INVALID", "驳回时处置动作必须为 NONE");
        }
        if (request.getNote() == null || request.getNote().isBlank()
                || request.getNote().length() > 500) {
            throw new BusinessException("SOFTWARE_REPORT_INVALID", "处理说明须为 1~500 字");
        }
        operationService.execute("ADMIN", adminId, SoftwareOperationService.OP_REPORT_HANDLE,
                idempotencyKey,
                Map.of("reportId", reportId, "status", request.getStatus(),
                        "action", action, "note", request.getNote()),
                null,
                (Supplier<Void>) () -> {
                    doHandle(adminId, reportId, request.getStatus(), action, request.getNote());
                    return null;
                },
                json -> null);
        return true;
    }

    private void doHandle(Long adminId, Long reportId, String status, String action, String note) {
        SoftwareReport report = reportMapper.selectById(reportId);
        if (report == null || report.getStatus() == SoftwareReportStatus.RESOLVED
                || report.getStatus() == SoftwareReportStatus.REJECTED) {
            // 幂等：无举报或已终态
            return;
        }
        report.setStatus(SoftwareReportStatus.valueOf(status));
        report.setHandlerId(adminId);
        report.setHandleNote(note + "（action=" + action + "）");
        report.setHandledAt(LocalDateTime.now(clock));
        report.setLockVersion(report.getLockVersion() + 1);
        reportMapper.updateById(report);

        if ("RESOLVED".equals(status)) {
            switch (action) {
                case "BAN_SOFTWARE" -> banSoftware(adminId, report.getSoftwareId(), note);
                case "HIDE_COMMENT" -> hideComment(adminId, report.getSoftwareId(),
                        report.getTargetId(), note);
                default -> {
                    // NONE：仅标记成立，不隐式处置（6.12）
                }
            }
        }
        writeAudit(report.getSoftwareId(), null, adminId, "REPORT_" + status,
                null, status, note);
    }

    /** 显式封禁：BANNED（作者不可自行恢复）；平台处置留审计 + 通知事件 */
    private void banSoftware(Long adminId, Long softwareId, String reason) {
        Software software = lockMapper.lockSoftware(softwareId);
        if (software == null || software.getLifecycle() == SoftwareLifecycle.BANNED) {
            return;
        }
        String fromStatus = software.getLifecycle().name();
        software.setLifecycle(SoftwareLifecycle.BANNED);
        softwareMapper.updateById(software);
        writeAudit(softwareId, null, adminId, "BAN", fromStatus, "BANNED", reason);
        outboxService.record(EventEnvelope.of(SoftwareEvents.TAG_SOFTWARE_BANNED, 1,
                String.valueOf(softwareId), 0, null,
                objectMapper.writeValueAsString(Map.of(
                        "softwareId", String.valueOf(softwareId),
                        "authorId", String.valueOf(software.getOwnerId()),
                        "reason", reason == null ? "" : reason))));
    }

    /** 隐藏评论：VISIBLE→HIDDEN 计数-1；HIDDEN 不回传原文（前台一致语义） */
    private void hideComment(Long adminId, Long softwareId, Long targetId, String reason) {
        if (targetId == null) {
            throw new BusinessException("SOFTWARE_REPORT_INVALID", "HIDE_COMMENT 需要目标评论 ID");
        }
        SoftwareComment comment = commentMapper.selectById(targetId);
        if (comment == null || !softwareId.equals(comment.getSoftwareId())) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "目标评论不存在或不属于该软件");
        }
        if (comment.getStatus() == SoftwareCommentStatus.VISIBLE) {
            comment.setStatus(SoftwareCommentStatus.HIDDEN);
            comment.setLockVersion(comment.getLockVersion() + 1);
            commentMapper.updateById(comment);
            softwareMapper.incrementCommentCount(softwareId, -1);
            writeAudit(softwareId, comment.getId(), adminId, "COMMENT_HIDE",
                    "VISIBLE", "HIDDEN", reason);
        }
    }

    @Override
    public Page<Map<String, Object>> feedbackPage(String status, int page, int size) {
        Page<SoftwareLinkFeedback> feedbackPage = feedbackMapper.selectPage(
                Page.of(Math.max(page, 1), Math.min(Math.max(size, 1), 100)),
                new LambdaQueryWrapper<SoftwareLinkFeedback>()
                        .eq(status != null && !status.isBlank(), SoftwareLinkFeedback::getStatus,
                                status == null ? null : LinkFeedbackStatus.valueOf(status))
                        .orderByAsc(SoftwareLinkFeedback::getCreatedAt));
        Page<Map<String, Object>> result = new Page<>(feedbackPage.getCurrent(),
                feedbackPage.getSize(), feedbackPage.getTotal());
        List<Map<String, Object>> rows = feedbackPage.getRecords().stream()
                .map(feedback -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("feedbackId", String.valueOf(feedback.getId()));
                    row.put("softwareId", String.valueOf(feedback.getSoftwareId()));
                    row.put("sourceKey", feedback.getSourceKey());
                    row.put("reporterId", String.valueOf(feedback.getReporterId()));
                    row.put("reasonCode", feedback.getReasonCode());
                    row.put("description", feedback.getDescription());
                    row.put("status", feedback.getStatus().name());
                    row.put("resolvedRevisionId", feedback.getResolvedRevisionId() == null
                            ? null : String.valueOf(feedback.getResolvedRevisionId()));
                    row.put("createdAt", String.valueOf(feedback.getCreatedAt()));
                    return row;
                })
                .toList();
        AdminRowEnricher.fillUserNicknames(rows, userSupportFeignClient, "reporterId", "reporterNickname");
        AdminRowEnricher.fillSoftwareNames(rows, softwareMapper, revisionMapper, "softwareId", "softwareName");
        result.setRecords(rows);
        return result;
    }

    @Override
    @Transactional
    public boolean markFeedback(Long adminId, Long feedbackId, String idempotencyKey,
                                   AdminFeedbackMarkRequest request) {
        if (request.getStatus() == null || !FEEDBACK_STATUSES.contains(request.getStatus())) {
            throw new BusinessException("SOFTWARE_FEEDBACK_INVALID",
                    "status 须为 VERIFYING/RESOLVED/REJECTED");
        }
        if ("RESOLVED".equals(request.getStatus()) && request.getResolvedRevisionId() == null) {
            throw new BusinessException("SOFTWARE_FEEDBACK_INVALID",
                    "RESOLVED 必须携带修复上线快照 ID（保留 original 指纹）");
        }
        operationService.execute("ADMIN", adminId, SoftwareOperationService.OP_FEEDBACK_MARK,
                idempotencyKey,
                Map.of("feedbackId", feedbackId, "status", request.getStatus(),
                        "resolvedRevisionId", String.valueOf(request.getResolvedRevisionId())),
                null,
                (Supplier<Void>) () -> {
                    doMark(adminId, feedbackId, request.getStatus(),
                            request.getResolvedRevisionId(), request.getNote());
                    return null;
                },
                json -> null);
        return true;
    }

    private void doMark(Long adminId, Long feedbackId, String status, Long resolvedRevisionId,
                        String note) {
        SoftwareLinkFeedback feedback = feedbackMapper.selectById(feedbackId);
        if (feedback == null) {
            return; // 幂等：无反馈
        }
        boolean terminal = feedback.getStatus() == LinkFeedbackStatus.RESOLVED
                || feedback.getStatus() == LinkFeedbackStatus.REJECTED;
        if (terminal) {
            if (feedback.getStatus().name().equals(status)) {
                return; // SW07：终态重复同意图，稳定返回原结果
            }
            if (!"VERIFYING".equals(status)) {
                throw new BusinessException("SOFTWARE_FEEDBACK_TERMINAL",
                        "反馈已终态（" + feedback.getStatus().name() + "），不能静默改为 "
                                + status + "；如需重开请先标记 VERIFYING");
            }
            // 显式重开：终态 → VERIFYING（管理员明确操作）
        }
        if ("RESOLVED".equals(status)) {
            validateResolvedFact(feedback, resolvedRevisionId);
        }
        feedback.setStatus(LinkFeedbackStatus.valueOf(status));
        feedback.setHandlerId(adminId);
        feedback.setHandleNote(note);
        if ("RESOLVED".equals(status)) {
            // 修复通过新快照上线：RESOLVED 保留 original 指纹与修复 revisionId（5.3）
            feedback.setResolvedRevisionId(resolvedRevisionId);
        }
        feedback.setHandledAt(LocalDateTime.now(clock));
        feedback.setLockVersion(feedback.getLockVersion() + 1);
        feedbackMapper.updateById(feedback);
        writeAudit(feedback.getSoftwareId(), null, adminId, "FEEDBACK_" + status,
                null, status, note == null ? "" : note);
    }

    /**
     * SW07：结案必须引用本软件当前已发布的修复快照，并具备具体修复事实之一——
     * ① 该来源已随修复快照上线移除；② 管理员在反馈之后核验 AVAILABLE 且指纹=当前内容
     * （换链接验证新指纹 / 同链接恢复均覆盖）。原始指纹永久保留在反馈行上。
     */
    private void validateResolvedFact(SoftwareLinkFeedback feedback, Long resolvedRevisionId) {
        Software software = softwareMapper.selectById(feedback.getSoftwareId());
        if (software == null || software.getPublishedRevisionId() == null
                || !software.getPublishedRevisionId().equals(resolvedRevisionId)) {
            throw new BusinessException("SOFTWARE_FEEDBACK_INVALID",
                    "结案必须引用本软件当前已发布的修复快照，不能填写任意 revisionId");
        }
        SoftwareRevision revision = revisionMapper.selectById(resolvedRevisionId);
        if (revision == null || revision.getReviewStatus() != SoftwareReviewStatus.APPROVED) {
            throw new BusinessException("SOFTWARE_FEEDBACK_INVALID", "修复快照不存在或未审核通过");
        }
        List<SoftwareRelease> releases = releaseMapper.selectList(
                new LambdaQueryWrapper<SoftwareRelease>()
                        .eq(SoftwareRelease::getRevisionId, resolvedRevisionId));
        List<SoftwareSource> sources = releases.isEmpty() ? List.of()
                : sourceMapper.selectList(new LambdaQueryWrapper<SoftwareSource>()
                        .in(SoftwareSource::getReleaseId,
                                releases.stream().map(SoftwareRelease::getId).toList()));
        if (sources.stream().noneMatch(s -> feedback.getSourceKey().equals(s.getSourceKey()))) {
            return; // 事实一：无效来源已随修复快照上线移除
        }
        SoftwareSource current = sources.stream()
                .filter(s -> feedback.getSourceKey().equals(s.getSourceKey()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("SOFTWARE_FEEDBACK_INVALID", "来源状态读取失败"));
        SoftwareSourceState state = sourceStateMapper.selectOne(
                new LambdaQueryWrapper<SoftwareSourceState>()
                        .eq(SoftwareSourceState::getSoftwareId, feedback.getSoftwareId())
                        .eq(SoftwareSourceState::getSourceKey, feedback.getSourceKey()));
        boolean verifiedNow = state != null
                && state.getAvailability() == SourceAvailability.AVAILABLE
                && state.getCheckedFingerprint() != null
                && state.getCheckedFingerprint().equals(current.getContentFingerprint())
                && state.getCheckedAt() != null
                && feedback.getCreatedAt() != null
                && state.getCheckedAt().isAfter(feedback.getCreatedAt());
        if (!verifiedNow) {
            throw new BusinessException("SOFTWARE_FEEDBACK_INVALID",
                    "缺少修复事实：需「该来源已随修复快照上线移除」或「管理员核验 AVAILABLE 且指纹=当前内容（核验时间晚于反馈）」");
        }
    }

    private void writeAudit(Long softwareId, Long revisionId, Long adminId, String action,
                            String from, String to, String reason) {
        SoftwareAuditLog logEntry = new SoftwareAuditLog();
        logEntry.setSoftwareId(softwareId);
        logEntry.setRevisionId(revisionId);
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
