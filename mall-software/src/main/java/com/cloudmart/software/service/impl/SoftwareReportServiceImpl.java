package com.cloudmart.software.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.dto.software.SoftwareAssetBindRequest;
import com.cloudmart.software.dto.software.SoftwareReportCreateRequest;
import com.cloudmart.software.entity.SoftwareComment;
import com.cloudmart.software.entity.SoftwareReport;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.enums.SoftwareCommentStatus;
import com.cloudmart.software.enums.SoftwareReportStatus;
import com.cloudmart.software.feign.SoftwareFileFeignClient;
import com.cloudmart.software.policy.SoftwareAccessPolicy;
import com.cloudmart.software.repository.SoftwareCommentMapper;
import com.cloudmart.software.repository.SoftwareReportMapper;
import com.cloudmart.software.service.SoftwareOperationService;
import com.cloudmart.software.service.SoftwareRateLimiter;
import com.cloudmart.software.service.SoftwareReportService;
import com.cloudmart.software.vo.software.SoftwareReportCreatedVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 用户侧举报实现（方案 7.3/6.12）。
 *
 * <p>不变量：只能举报当前公开可见的软件、或该软件下的可见评论（不可见目标一律
 * 通用不存在提示，不泄露草稿/封禁状态）；证据素材按 SOFTWARE_REPORT 引用键
 * best-effort 绑定（绑定失败不回滚举报，WARN 留痕）；举报创建走
 * operation(USER) 幂等，与软件库同事务。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoftwareReportServiceImpl implements SoftwareReportService {

    private static final Set<String> TARGET_TYPES = Set.of("SOFTWARE", "SOFTWARE_COMMENT");
    private static final int MAX_REASON_LENGTH = 50;
    private static final int MAX_DESCRIPTION_LENGTH = 500;
    private static final int MAX_EVIDENCE_COUNT = 6;

    private final SoftwareAccessPolicy accessPolicy;
    private final SoftwareCommentMapper commentMapper;
    private final SoftwareReportMapper reportMapper;
    private final SoftwareOperationService operationService;
    private final SoftwareRateLimiter rateLimiter;
    private final SoftwareFileFeignClient fileFeignClient;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Override
    public SoftwareReportCreatedVO create(Long userId, Long softwareId, String idempotencyKey,
                                          SoftwareReportCreateRequest request) {
        // 可见性：仅可举报公开可见软件（不可见目标统一 SOFTWARE_NOT_FOUND，不泄露状态）
        SoftwareRevision published = accessPolicy.visiblePublishedRevision(softwareId);
        if (published == null) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
        }
        String targetType = request.getTargetType() == null ? "" : request.getTargetType().trim();
        if (!TARGET_TYPES.contains(targetType)) {
            throw new BusinessException("SOFTWARE_REPORT_INVALID",
                    "targetType 须为 SOFTWARE/SOFTWARE_COMMENT");
        }
        Long targetId;
        if ("SOFTWARE".equals(targetType)) {
            targetId = softwareId;
        } else {
            if (request.getTargetId() == null) {
                throw new BusinessException("SOFTWARE_REPORT_INVALID", "评论举报必须携带 targetId");
            }
            SoftwareComment comment = commentMapper.selectById(request.getTargetId());
            if (comment == null || !softwareId.equals(comment.getSoftwareId())
                    || comment.getStatus() != SoftwareCommentStatus.VISIBLE) {
                throw new BusinessException("SOFTWARE_NOT_FOUND", "目标评论不存在");
            }
            targetId = comment.getId();
        }
        String reason = request.getReason() == null ? "" : request.getReason().trim();
        if (reason.isEmpty() || reason.length() > MAX_REASON_LENGTH) {
            throw new BusinessException("SOFTWARE_REPORT_INVALID", "举报理由须为 1~50 字");
        }
        String description = request.getDescription() == null ? null
                : request.getDescription().trim();
        if (description != null && description.isEmpty()) {
            description = null;
        }
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new BusinessException("SOFTWARE_REPORT_INVALID", "补充说明不能超过 500 字");
        }
        List<Long> evidence = request.getEvidenceAssetIds() == null ? List.of()
                : request.getEvidenceAssetIds().stream().filter(Objects::nonNull).distinct().toList();
        if (evidence.size() > MAX_EVIDENCE_COUNT) {
            throw new BusinessException("SOFTWARE_REPORT_INVALID", "证据素材最多 6 个");
        }
        if (!rateLimiter.allowReport(userId)) {
            throw new BusinessException("SOFTWARE_RATE_LIMITED", "举报过于频繁，请稍后再试");
        }

        String finalDescription = description;
        Long finalTargetId = targetId;
        CreatedReport created = transactionTemplate.execute(status -> operationService.execute(
                "USER", userId, SoftwareOperationService.OP_CREATE_REPORT, idempotencyKey,
                Map.of("softwareId", softwareId, "targetType", targetType,
                        "targetId", String.valueOf(finalTargetId), "reason", reason,
                        "description", finalDescription == null ? "" : finalDescription),
                softwareId,
                () -> doCreate(userId, softwareId, targetType, finalTargetId, reason,
                        finalDescription, evidence),
                json -> objectMapper.readValue(json, CreatedReport.class)));
        Objects.requireNonNull(created);
        bindEvidenceSoft(created.reportId(), userId, evidence);
        return new SoftwareReportCreatedVO(created.reportId(), created.status());
    }

    /** 创建结果（只存 ID/状态，6.10） */
    private record CreatedReport(Long reportId, String status) {
    }

    /** 短事务内落库（仅由 TransactionTemplate + operation 调用） */
    private CreatedReport doCreate(Long userId, Long softwareId, String targetType, Long targetId,
                                   String reason, String description, List<Long> evidence) {
        SoftwareReport report = new SoftwareReport();
        report.setReporterId(userId);
        report.setSoftwareId(softwareId);
        report.setTargetType(targetType);
        report.setTargetId(targetId);
        report.setReason(reason);
        report.setDescription(description);
        if (!evidence.isEmpty()) {
            report.setEvidenceAssetIds(objectMapper.writeValueAsString(evidence));
        }
        report.setStatus(SoftwareReportStatus.OPEN);
        report.setLockVersion(0);
        LocalDateTime now = LocalDateTime.now(clock);
        report.setCreatedAt(now);
        report.setUpdatedAt(now);
        reportMapper.insert(report);
        return new CreatedReport(report.getId(), report.getStatus().name());
    }

    /**
     * 证据素材引用绑定（8.2 引用键：SOFTWARE_REPORT + reportId）：best-effort——
     * 绑定失败只 WARN，不回滚已提交的举报（举报本身已可被管理员处置）；
     * 同引用键重试幂等，后续用户重复提交（同幂等键重放）会再次尝试补齐。
     */
    private void bindEvidenceSoft(Long reportId, Long userId, List<Long> evidence) {
        for (Long assetId : evidence) {
            try {
                var response = fileFeignClient.bindReference(assetId,
                        SoftwareAssetBindRequest.reportEvidence(reportId, userId));
                if (response == null || response.data() == null) {
                    log.warn("举报证据绑定未成功 reportId={} assetId={}", reportId, assetId);
                }
            } catch (Exception e) {
                log.warn("举报证据绑定失败 reportId={} assetId={}: {}", reportId, assetId,
                        e.getMessage());
            }
        }
    }
}
