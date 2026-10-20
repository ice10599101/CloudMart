package com.cloudmart.software.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.dto.software.SourceAppealCreateRequest;
import com.cloudmart.software.dto.software.SourceAppealCreatedVO;
import com.cloudmart.software.dto.software.SourceAppealHandleRequest;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.entity.SoftwareSourceAppeal;
import com.cloudmart.software.entity.SoftwareSourceState;
import com.cloudmart.software.enums.SourceAvailability;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareRevisionMapper;
import com.cloudmart.software.repository.SoftwareSourceAppealMapper;
import com.cloudmart.software.repository.SoftwareSourceStateMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * SF08 补批：作者来源申诉。
 *
 * <p>作者只能对本人已发布软件、且当前存在处置（INVALID/DISABLED/SUSPECTED）的来源申诉；
 * 提交保存处置快照并关联处置 ID，同一来源同时仅允许一条 PENDING。管理员处理只落一次结论：
 * ACCEPTED 把来源恢复为 UNKNOWN 待重检（不直接判 AVAILABLE，仍由巡检/人工核实），
 * REJECTED 保留原处置。处理说明必填，审计留痕。</p>
 */
@Service
@RequiredArgsConstructor
public class SoftwareSourceAppealService {

    private static final Set<SourceAvailability> APPEALABLE =
            Set.of(SourceAvailability.INVALID, SourceAvailability.DISABLED,
                    SourceAvailability.SUSPECTED);
    private static final Set<String> DECISIONS = Set.of("ACCEPTED", "REJECTED");

    private final SoftwareMapper softwareMapper;
    private final SoftwareRevisionMapper revisionMapper;
    private final SoftwareSourceStateMapper sourceStateMapper;
    private final SoftwareSourceAppealMapper appealMapper;
    private final SoftwareOperationService operationService;
    private final AdminSoftwareSourceService adminSoftwareSourceService;
    private final SoftwareAuditService auditService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /** 作者提交申诉（幂等键托管；并发重复 PENDING 由唯一键兜底）。 */
    @Transactional
    public SourceAppealCreatedVO submit(Long userId, Long softwareId,
                                        SourceAppealCreateRequest request,
                                        String idempotencyKey) {
        Software software = owned(userId, softwareId);
        if (software.getPublishedRevisionId() == null) {
            throw new BusinessException("SOFTWARE_STATE_CONFLICT", "仅已发布内容可申诉来源处置");
        }
        String sourceKey = trimToNull(request.getSourceKey());
        String reason = trimToNull(request.getReason());
        String evidence = trimToNull(request.getEvidence());
        if (sourceKey == null || sourceKey.length() > 128) {
            throw new BusinessException("SOFTWARE_SOURCE_APPEAL_INVALID", "sourceKey 须为 1~128 字符");
        }
        if (reason == null || reason.length() < 10 || reason.length() > 1000) {
            throw new BusinessException("SOFTWARE_SOURCE_APPEAL_INVALID", "申诉理由须为 10~1000 字");
        }
        if (evidence != null && evidence.length() > 2000) {
            throw new BusinessException("SOFTWARE_SOURCE_APPEAL_INVALID", "证据说明最长 2000 字");
        }
        SoftwareSourceState state = sourceStateMapper.selectOne(
                new LambdaQueryWrapper<SoftwareSourceState>()
                        .eq(SoftwareSourceState::getSoftwareId, softwareId)
                        .eq(SoftwareSourceState::getSourceKey, sourceKey));
        if (state == null || state.getAvailability() == null
                || !APPEALABLE.contains(state.getAvailability())) {
            throw new BusinessException("SOFTWARE_SOURCE_APPEAL_INVALID",
                    "该来源当前没有可申诉的处置");
        }

        Map<String, Object> intent = new LinkedHashMap<>();
        intent.put("softwareId", softwareId);
        intent.put("sourceKey", sourceKey);
        intent.put("reason", reason);
        intent.put("evidence", evidence == null ? "" : evidence);
        return operationService.execute("USER", userId,
                SoftwareOperationService.OP_SOURCE_APPEAL, idempotencyKey, intent, softwareId,
                () -> doSubmit(software, userId, sourceKey, reason, evidence, state),
                json -> objectMapper.readValue(json, SourceAppealCreatedVO.class));
    }

    private SourceAppealCreatedVO doSubmit(Software software, Long userId, String sourceKey,
                                           String reason, String evidence,
                                           SoftwareSourceState state) {
        SoftwareSourceAppeal appeal = new SoftwareSourceAppeal();
        appeal.setSoftwareId(software.getId());
        appeal.setSourceKey(sourceKey);
        appeal.setDispositionId(state.getId());
        appeal.setDispositionAvailability(state.getAvailability().name());
        appeal.setDispositionReason(state.getReason());
        appeal.setDispositionAt(state.getCheckedAt());
        appeal.setAppellantId(userId);
        appeal.setReason(reason);
        appeal.setEvidence(evidence);
        appeal.setStatus("PENDING");
        try {
            appealMapper.insert(appeal);
        } catch (DuplicateKeyException e) {
            // 唯一键（software_id, source_key, pending_guard）：同一来源只能有一条待处理申诉
            throw new BusinessException("SOFTWARE_APPEAL_EXISTS", "该来源已有待处理申诉");
        }
        auditService.record(software.getId(), software.getPublishedRevisionId(), userId, "USER",
                "SOURCE_APPEAL", state.getAvailability().name(), "PENDING",
                "appealId=" + appeal.getId() + " sourceKey=" + sourceKey
                        + " dispositionId=" + state.getId(), null);
        return new SourceAppealCreatedVO(appeal.getId(), "PENDING");
    }

    /** 作者本人的申诉记录（倒序，最多 100 条）。 */
    public List<Map<String, Object>> mine(Long userId) {
        List<Software> mine = softwareMapper.selectList(new LambdaQueryWrapper<Software>()
                .eq(Software::getOwnerId, userId)
                .in(Software::getLifecycle, "ACTIVE", "UNLISTED"));
        if (mine.isEmpty()) {
            return List.of();
        }
        Map<Long, String> nameById = publishedNames(mine);
        List<Long> softwareIds = mine.stream().map(Software::getId).toList();
        List<SoftwareSourceAppeal> rows = appealMapper.selectList(
                new LambdaQueryWrapper<SoftwareSourceAppeal>()
                        .in(SoftwareSourceAppeal::getSoftwareId, softwareIds)
                        .orderByDesc(SoftwareSourceAppeal::getId)
                        .last("LIMIT 100"));
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (SoftwareSourceAppeal appeal : rows) {
            result.add(row(appeal, nameById.get(appeal.getSoftwareId())));
        }
        return result;
    }

    /** 管理端申诉列表（status 过滤；含软件名与处置快照）。 */
    public Page<Map<String, Object>> page(String status, int page, int size) {
        LambdaQueryWrapper<SoftwareSourceAppeal> query =
                new LambdaQueryWrapper<SoftwareSourceAppeal>()
                        .orderByDesc(SoftwareSourceAppeal::getId);
        if (status != null && !status.isBlank()) {
            String normalized = status.toUpperCase(Locale.ROOT);
            if (!Set.of("PENDING", "ACCEPTED", "REJECTED").contains(normalized)) {
                throw new BusinessException("VALIDATION_ERROR", "status 须为 PENDING/ACCEPTED/REJECTED");
            }
            query.eq(SoftwareSourceAppeal::getStatus, normalized);
        }
        Page<SoftwareSourceAppeal> result = appealMapper.selectPage(
                Page.of(Math.max(page, 1), Math.min(Math.max(size, 1), 100)), query);
        List<Long> softwareIds = result.getRecords().stream()
                .map(SoftwareSourceAppeal::getSoftwareId).distinct().toList();
        Map<Long, String> nameById = new HashMap<>();
        if (!softwareIds.isEmpty()) {
            List<Software> softwareRows = softwareMapper.selectList(
                    new LambdaQueryWrapper<Software>().in(Software::getId, softwareIds));
            nameById.putAll(publishedNames(softwareRows));
        }
        Page<Map<String, Object>> view = Page.of(result.getCurrent(), result.getSize(),
                result.getTotal());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SoftwareSourceAppeal appeal : result.getRecords()) {
            rows.add(row(appeal, nameById.get(appeal.getSoftwareId())));
        }
        view.setRecords(rows);
        return view;
    }

    /**
     * 管理员处理申诉：只允许 PENDING → ACCEPTED/REJECTED 一次。
     * ACCEPTED 复用人工作业把来源恢复为 UNKNOWN（保留审计），不直接判可用。
     */
    @Transactional
    public boolean handle(Long adminId, Long appealId, SourceAppealHandleRequest request) {
        String decision = request.getDecision() == null ? null
                : request.getDecision().trim().toUpperCase(Locale.ROOT);
        String note = trimToNull(request.getNote());
        if (decision == null || !DECISIONS.contains(decision)) {
            throw new BusinessException("SOFTWARE_SOURCE_APPEAL_INVALID",
                    "decision 须为 ACCEPTED/REJECTED");
        }
        if (note == null || note.length() > 500) {
            throw new BusinessException("SOFTWARE_SOURCE_APPEAL_INVALID", "处理说明须为 1~500 字");
        }
        SoftwareSourceAppeal appeal = appealMapper.selectById(appealId);
        if (appeal == null) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "申诉不存在");
        }
        if (!"PENDING".equals(appeal.getStatus())) {
            if (decision.equals(appeal.getStatus())) {
                return true; // 重复提交同一结论：幂等返回
            }
            throw new BusinessException("SOFTWARE_STATE_CONFLICT",
                    "申诉已处理为 " + appeal.getStatus() + "，不能改为 " + decision);
        }
        int updated = appealMapper.handle(appealId, decision, adminId, note,
                LocalDateTime.now(clock));
        if (updated == 0) {
            SoftwareSourceAppeal current = appealMapper.selectById(appealId);
            if (current != null && decision.equals(current.getStatus())) {
                return true;
            }
            throw new BusinessException("SOFTWARE_STATE_CONFLICT", "申诉已被其他管理员处理，请刷新");
        }
        if ("ACCEPTED".equals(decision)) {
            adminSoftwareSourceService.applyState(adminId, appeal.getSoftwareId(),
                    appeal.getSourceKey(), "UNKNOWN",
                    truncate("作者申诉通过：" + note, 500));
        }
        Software software = softwareMapper.selectById(appeal.getSoftwareId());
        auditService.record(appeal.getSoftwareId(),
                software == null ? null : software.getPublishedRevisionId(), adminId, "ADMIN",
                "ACCEPTED".equals(decision) ? "SOURCE_APPEAL_ACCEPT" : "SOURCE_APPEAL_REJECT",
                appeal.getDispositionAvailability(), decision,
                "appealId=" + appealId + " sourceKey=" + appeal.getSourceKey()
                        + " note=" + note, null);
        return true;
    }

    private Software owned(Long userId, Long softwareId) {
        Software software = softwareMapper.selectById(softwareId);
        if (software == null || software.getDeletedAt() != null
                || !userId.equals(software.getOwnerId())) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
        }
        return software;
    }

    private Map<Long, String> publishedNames(List<Software> rows) {
        Map<Long, String> names = new HashMap<>();
        for (Software software : rows) {
            SoftwareRevision revision = software.getPublishedRevisionId() == null ? null
                    : revisionMapper.selectById(software.getPublishedRevisionId());
            names.put(software.getId(), revision == null ? null : revision.getName());
        }
        return names;
    }

    private Map<String, Object> row(SoftwareSourceAppeal appeal, String softwareName) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("appealId", String.valueOf(appeal.getId()));
        row.put("softwareId", String.valueOf(appeal.getSoftwareId()));
        row.put("softwareName", softwareName);
        row.put("sourceKey", appeal.getSourceKey());
        row.put("dispositionId", appeal.getDispositionId() == null ? null
                : String.valueOf(appeal.getDispositionId()));
        row.put("dispositionAvailability", appeal.getDispositionAvailability());
        row.put("dispositionReason", appeal.getDispositionReason());
        row.put("dispositionAt", appeal.getDispositionAt() == null ? null
                : appeal.getDispositionAt().toString());
        row.put("appellantId", String.valueOf(appeal.getAppellantId()));
        row.put("reason", appeal.getReason());
        row.put("evidence", appeal.getEvidence());
        row.put("status", appeal.getStatus());
        row.put("handlerId", appeal.getHandlerId() == null ? null
                : String.valueOf(appeal.getHandlerId()));
        row.put("handleNote", appeal.getHandleNote());
        row.put("handledAt", appeal.getHandledAt() == null ? null
                : appeal.getHandledAt().toString());
        row.put("createdAt", appeal.getCreatedAt() == null ? null
                : appeal.getCreatedAt().toString());
        return row;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
