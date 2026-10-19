package com.cloudmart.software.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.async.EventEnvelope;
import com.cloudmart.common.async.outbox.OutboxService;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.entity.SoftwareAuditLog;
import com.cloudmart.software.entity.SoftwarePermissionAudit;
import com.cloudmart.software.entity.SoftwarePublisherApplication;
import com.cloudmart.software.entity.SoftwarePublisherPermission;
import com.cloudmart.software.mq.SoftwareEvents;
import com.cloudmart.software.repository.SoftwareAuditLogMapper;
import com.cloudmart.software.repository.SoftwareLockMapper;
import com.cloudmart.software.repository.SoftwarePermissionAuditMapper;
import com.cloudmart.software.repository.SoftwarePublisherApplicationMapper;
import com.cloudmart.software.repository.SoftwarePublisherPermissionMapper;
import com.cloudmart.software.service.SoftwareOperationService;
import com.cloudmart.software.service.SoftwarePublisherApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 发布者申请实现（SF03）。
 *
 * <p>不变量：每用户最多一条 PENDING（DB 生成列唯一键收敛并发，不"先查再插"）；
 * 已授权（can_publish=1）不再创建申请；通过决定与资格变更/targetUserId 审计同事务；
 * 结果经 Outbox 通知用户（载荷只含标识与可公开原因，不含证据内容）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoftwarePublisherApplicationServiceImpl implements SoftwarePublisherApplicationService {

    private static final int SCHEMA_VERSION = 1;

    private final SoftwarePublisherApplicationMapper applicationMapper;
    private final SoftwarePublisherPermissionMapper publisherPermissionMapper;
    private final SoftwarePermissionAuditMapper permissionAuditMapper;
    private final SoftwareLockMapper lockMapper;
    private final SoftwareAuditLogMapper auditLogMapper;
    private final SoftwareOperationService operationService;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    @Transactional
    public Map<String, Object> submit(Long userId, String idempotencyKey, String intro,
                                      String intendedCategories, String evidenceAssetIds) {
        if (intro == null || intro.isBlank() || intro.length() > 1000) {
            throw new BusinessException("SOFTWARE_APPLICATION_INVALID", "发布用途与来源说明须为 1~1000 字");
        }
        if (intendedCategories == null || intendedCategories.isBlank()
                || intendedCategories.length() > 500) {
            throw new BusinessException("SOFTWARE_APPLICATION_INVALID", "拟投分类须为 1~500 字");
        }
        // 已授权者不可申请（资格为最终事实）
        SoftwarePublisherPermission permission = publisherPermissionMapper.selectOne(
                new LambdaQueryWrapper<SoftwarePublisherPermission>()
                        .eq(SoftwarePublisherPermission::getUserId, userId));
        if (permission != null && Boolean.TRUE.equals(permission.getCanPublish())) {
            throw new BusinessException("SOFTWARE_APPLICATION_STATE", "您已具备发布资格，无需申请");
        }
        operationService.execute("USER", userId, SoftwareOperationService.OP_PUBLISHER_APPLY,
                idempotencyKey,
                Map.of("intro", intro, "intendedCategories", intendedCategories),
                null,
                (Supplier<Map<String, Object>>) () -> {
                    SoftwarePublisherApplication application = new SoftwarePublisherApplication();
                    application.setUserId(userId);
                    application.setStatus("PENDING");
                    application.setIntro(intro.trim());
                    application.setIntendedCategories(intendedCategories.trim());
                    application.setEvidenceAssetIds(evidenceAssetIds);
                    application.setSubmittedAt(LocalDateTime.now(clock));
                    try {
                        applicationMapper.insert(application);
                    } catch (DuplicateKeyException e) {
                        // 未结申请已存在：返回原记录（幂等），不报 500
                        SoftwarePublisherApplication existing = applicationMapper.selectOne(
                                pendingOf(userId));
                        if (existing == null) {
                            throw new BusinessException("SOFTWARE_OPERATION_CONFLICT",
                                    "申请提交冲突，请刷新后重试");
                        }
                        return toVo(existing);
                    }
                    return toVo(application);
                },
                json -> null);
        // execute 返回 void，这里重查返回结果（同事务内可见自身写入）
        SoftwarePublisherApplication latest = applicationMapper.selectOne(pendingOf(userId));
        if (latest == null) {
            throw new BusinessException("SOFTWARE_OPERATION_CONFLICT", "申请提交失败，请重试");
        }
        return toVo(latest);
    }

    @Override
    public Page<Map<String, Object>> mine(Long userId, int page, int size) {
        Page<SoftwarePublisherApplication> result = applicationMapper.selectPage(
                Page.of(Math.max(page, 1), Math.min(Math.max(size, 1), 50)),
                new LambdaQueryWrapper<SoftwarePublisherApplication>()
                        .eq(SoftwarePublisherApplication::getUserId, userId)
                        .orderByDesc(SoftwarePublisherApplication::getId));
        return toPage(result);
    }

    @Override
    @Transactional
    public boolean cancel(Long userId, Long applicationId, String idempotencyKey) {
        operationService.execute("USER", userId, SoftwareOperationService.OP_PUBLISHER_CANCEL,
                idempotencyKey,
                Map.of("applicationId", applicationId),
                null,
                (Supplier<Void>) () -> {
                    SoftwarePublisherApplication application =
                            applicationMapper.selectById(applicationId);
                    if (application == null || !application.getUserId().equals(userId)) {
                        return null; // 他人申请按"不存在"处理，避免探测
                    }
                    if (!"PENDING".equals(application.getStatus())) {
                        return null; // 已终态重复同意图：幂等
                    }
                    application.setStatus("CANCELLED");
                    application.setReviewedAt(LocalDateTime.now(clock));
                    applicationMapper.updateById(application);
                    return null;
                },
                json -> null);
        return true;
    }

    @Override
    public Page<Map<String, Object>> adminPage(String status, int page, int size) {
        Page<SoftwarePublisherApplication> result = applicationMapper.selectPage(
                Page.of(Math.max(page, 1), Math.min(Math.max(size, 1), 100)),
                new LambdaQueryWrapper<SoftwarePublisherApplication>()
                        .eq(status != null && !status.isBlank(),
                                SoftwarePublisherApplication::getStatus, status)
                        .orderByAsc(SoftwarePublisherApplication::getId));
        return toPage(result);
    }

    @Override
    @Transactional
    public boolean decide(Long adminId, Long applicationId, boolean approve,
                          String reason, String idempotencyKey) {
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new BusinessException("SOFTWARE_APPLICATION_INVALID", "决定说明须为 1~500 字");
        }
        SoftwarePublisherApplication application = applicationMapper.selectById(applicationId);
        if (application == null) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "申请不存在");
        }
        String status = application.getStatus();
        if ("APPROVED".equals(status) || "REJECTED".equals(status)) {
            if (status.equals(approve ? "APPROVED" : "REJECTED")) {
                return true; // 终态重复同意图：幂等
            }
            throw new BusinessException("SOFTWARE_APPLICATION_TERMINAL",
                    "申请已终态（" + status + "），不能改为其他结论");
        }
        if (!"PENDING".equals(status)) {
            throw new BusinessException("SOFTWARE_APPLICATION_STATE",
                    "申请已被申请人撤回，不能审核");
        }
        Long userId = application.getUserId();
        if (approve) {
            // 通过：同事务授予资格（复用统一资格动作）+ targetUserId 审计
            grantAndAudit(adminId, userId, reason, idempotencyKey);
        }
        application.setStatus(approve ? "APPROVED" : "REJECTED");
        application.setReviewedBy(adminId);
        application.setReviewedAt(LocalDateTime.now(clock));
        application.setReason(reason.trim());
        applicationMapper.updateById(application);
        // 资格变更留软件审计（与操作同事务）
        SoftwareAuditLog audit = new SoftwareAuditLog();
        audit.setSoftwareId(null);
        audit.setActorId(adminId);
        audit.setActorType("ADMIN");
        audit.setAction(approve ? "PUBLISHER_APPLICATION_APPROVE" : "PUBLISHER_APPLICATION_REJECT");
        audit.setFromStatus(status);
        audit.setToStatus(application.getStatus());
        audit.setReason(reason.trim());
        audit.setCreatedAt(LocalDateTime.now(clock));
        auditLogMapper.insert(audit);
        // 结果通知（载荷不含证据内容）
        outboxService.record(EventEnvelope.of(SoftwareEvents.TAG_PUBLISHER_APPLICATION,
                SCHEMA_VERSION, String.valueOf(userId), System.currentTimeMillis(), null,
                objectMapper.writeValueAsString(Map.of(
                        "userId", String.valueOf(userId),
                        "status", application.getStatus(),
                        "reason", reason.trim()))));
        return true;
    }

    /** 与 AdminSoftwarePermissionServiceImpl.toggle 同一套资格动作：锁行 → upsert → targetUserId 审计 */
    private void grantAndAudit(Long adminId, Long userId, String reason, String requestId) {
        SoftwarePublisherPermission permission = lockMapper.lockPublisherByUser(userId);
        String before = null;
        if (permission == null) {
            permission = new SoftwarePublisherPermission();
            permission.setUserId(userId);
            permission.setCanPublish(true);
            permission.setReviewRequired(true);
            permission.setUpdatedBy(adminId);
            permission.setReason("发布者申请通过：" + reason);
            permission.setLockVersion(0);
            try {
                publisherPermissionMapper.insert(permission);
            } catch (DuplicateKeyException e) {
                throw new BusinessException("SOFTWARE_OPERATION_CONFLICT", "资格记录已存在，请刷新后重试");
            }
        } else {
            before = toSnapshotJson(permission.getCanPublish(), permission.getPublishMode());
            permission.setCanPublish(true);
            permission.setReviewRequired(true);
            permission.setUpdatedBy(adminId);
            permission.setReason("发布者申请通过：" + reason);
            permission.setLockVersion(permission.getLockVersion() + 1);
            publisherPermissionMapper.updateById(permission);
        }
        SoftwarePermissionAudit audit = new SoftwarePermissionAudit();
        audit.setTargetUserId(userId);
        audit.setActorAdminId(adminId);
        audit.setAction("PUBLISH_ENABLE");
        audit.setBeforeJson(before);
        audit.setAfterJson(toSnapshotJson(true, "REVIEW"));
        audit.setReason("发布者申请通过：" + reason);
        audit.setRequestId(requestId);
        audit.setCreatedAt(LocalDateTime.now(clock));
        permissionAuditMapper.insert(audit);
    }

    private LambdaQueryWrapper<SoftwarePublisherApplication> pendingOf(Long userId) {
        return new LambdaQueryWrapper<SoftwarePublisherApplication>()
                .eq(SoftwarePublisherApplication::getUserId, userId)
                .eq(SoftwarePublisherApplication::getStatus, "PENDING")
                .orderByDesc(SoftwarePublisherApplication::getId)
                .last("LIMIT 1");
    }

    private Map<String, Object> toVo(SoftwarePublisherApplication application) {
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("applicationId", String.valueOf(application.getId()));
        vo.put("userId", String.valueOf(application.getUserId()));
        vo.put("status", application.getStatus());
        vo.put("intro", application.getIntro());
        vo.put("intendedCategories", application.getIntendedCategories());
        vo.put("reason", application.getReason());
        vo.put("submittedAt", String.valueOf(application.getSubmittedAt()));
        vo.put("reviewedAt", application.getReviewedAt() == null
                ? null : String.valueOf(application.getReviewedAt()));
        return vo;
    }

    private Page<Map<String, Object>> toPage(Page<SoftwarePublisherApplication> result) {
        Page<Map<String, Object>> page = new Page<>(result.getCurrent(), result.getSize(),
                result.getTotal());
        page.setRecords(result.getRecords().stream().map(this::toVoAdmin).toList());
        return page;
    }

    /** 后台视图：附申请人标识（昵称由代理层 AdminRowEnricher 补齐） */
    private Map<String, Object> toVoAdmin(SoftwarePublisherApplication application) {
        Map<String, Object> vo = toVo(application);
        vo.put("reviewedBy", application.getReviewedBy() == null
                ? null : String.valueOf(application.getReviewedBy()));
        return vo;
    }

    private String toSnapshotJson(Boolean canPublish, String publishMode) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("canPublish", canPublish);
        payload.put("publishMode", publishMode);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return String.valueOf(canPublish);
        }
    }
}
