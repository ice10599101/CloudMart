package com.cloudmart.software.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.dto.software.AdminGrantRequest;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareAccessRequest;
import com.cloudmart.software.entity.SoftwareAuditLog;
import com.cloudmart.software.enums.SoftwareAccessMode;
import com.cloudmart.software.repository.SoftwareAccessRequestMapper;
import com.cloudmart.software.repository.SoftwareAuditLogMapper;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.service.AdminSoftwarePermissionService;
import com.cloudmart.software.service.SoftwareAccessRequestService;
import com.cloudmart.software.service.SoftwareOperationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 受限来源访问申请实现（SF04）。
 *
 * <p>不变量：软件须为 ADMIN_PAID 访问模式；每用户每软件最多一条 PENDING（DB 生成列唯一键）；
 * 批准同事务调用既有授权服务（grant 幂等键绑定申请 ID，同意图重试不重复授权）并回写
 * grant_id；审批不替换申请人；用户只能读/撤自己的申请；撤销授权保留申请与审计事实。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoftwareAccessRequestServiceImpl implements SoftwareAccessRequestService {

    private final SoftwareAccessRequestMapper requestMapper;
    private final SoftwareMapper softwareMapper;
    private final com.cloudmart.software.repository.SoftwareRevisionMapper revisionMapper;
    private final SoftwareAuditLogMapper auditLogMapper;
    private final com.cloudmart.software.repository.SoftwareAccessGrantMapper accessGrantMapper;
    private final AdminSoftwarePermissionService adminSoftwarePermissionService;
    private final SoftwareOperationService operationService;
    private final Clock clock;

    @Override
    @Transactional
    public Map<String, Object> submit(Long userId, Long softwareId, String idempotencyKey,
                                      String message, String evidenceAssetIds) {
        if (message == null || message.isBlank() || message.length() > 1000) {
            throw new BusinessException("SOFTWARE_ACCESS_REQUEST_INVALID", "申请说明须为 1~1000 字");
        }
        Software software = softwareMapper.selectById(softwareId);
        if (software == null || software.getDeletedAt() != null) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
        }
        // 访问模式在当前线上快照上（4.3）；无已发布内容不可申请
        if (software.getPublishedRevisionId() == null) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在或暂无上线内容");
        }
        var revision = revisionMapper.selectById(software.getPublishedRevisionId());
        if (revision == null || revision.getAccessMode() != SoftwareAccessMode.ADMIN_PAID) {
            throw new BusinessException("SOFTWARE_ACCESS_REQUEST_INVALID",
                    "该软件不是付款查看模式，无需申请访问");
        }
        operationService.execute("USER", userId, SoftwareOperationService.OP_ACCESS_REQUEST,
                idempotencyKey,
                Map.of("softwareId", softwareId, "message", message.trim()),
                softwareId,
                (Supplier<Map<String, Object>>) () -> {
                    SoftwareAccessRequest request = new SoftwareAccessRequest();
                    request.setSoftwareId(softwareId);
                    request.setApplicantId(userId);
                    request.setStatus("PENDING");
                    request.setMessage(message.trim());
                    request.setEvidenceAssetIds(evidenceAssetIds);
                    request.setCreatedAt(LocalDateTime.now(clock));
                    try {
                        requestMapper.insert(request);
                    } catch (DuplicateKeyException e) {
                        SoftwareAccessRequest existing = requestMapper.selectOne(
                                pendingOf(userId, softwareId));
                        if (existing == null) {
                            throw new BusinessException("SOFTWARE_OPERATION_CONFLICT",
                                    "申请提交冲突，请刷新后重试");
                        }
                        return toVo(existing);
                    }
                    return toVo(request);
                },
                json -> null);
        SoftwareAccessRequest latest = requestMapper.selectOne(pendingOf(userId, softwareId));
        if (latest == null) {
            throw new BusinessException("SOFTWARE_OPERATION_CONFLICT", "申请提交失败，请重试");
        }
        return toVo(latest);
    }

    @Override
    public Page<Map<String, Object>> mine(Long userId, int page, int size) {
        Page<SoftwareAccessRequest> result = requestMapper.selectPage(
                Page.of(Math.max(page, 1), Math.min(Math.max(size, 1), 50)),
                new LambdaQueryWrapper<SoftwareAccessRequest>()
                        .eq(SoftwareAccessRequest::getApplicantId, userId)
                        .orderByDesc(SoftwareAccessRequest::getId));
        return toPage(result);
    }

    @Override
    @Transactional
    public boolean cancel(Long userId, Long applicationId, String idempotencyKey) {
        operationService.execute("USER", userId, SoftwareOperationService.OP_ACCESS_REQUEST_CANCEL,
                idempotencyKey,
                Map.of("applicationId", applicationId),
                null,
                (Supplier<Void>) () -> {
                    SoftwareAccessRequest request = requestMapper.selectById(applicationId);
                    if (request == null || !request.getApplicantId().equals(userId)) {
                        return null;
                    }
                    if (!"PENDING".equals(request.getStatus())) {
                        return null;
                    }
                    request.setStatus("CANCELLED");
                    request.setHandledAt(LocalDateTime.now(clock));
                    requestMapper.updateById(request);
                    return null;
                },
                json -> null);
        return true;
    }

    @Override
    public Page<Map<String, Object>> adminPage(String status, int page, int size) {
        Page<SoftwareAccessRequest> result = requestMapper.selectPage(
                Page.of(Math.max(page, 1), Math.min(Math.max(size, 1), 100)),
                new LambdaQueryWrapper<SoftwareAccessRequest>()
                        .eq(status != null && !status.isBlank(),
                                SoftwareAccessRequest::getStatus, status)
                        .orderByAsc(SoftwareAccessRequest::getId));
        return toPage(result);
    }

    @Override
    @Transactional
    public boolean decide(Long adminId, Long applicationId, boolean approve,
                          String reason, String idempotencyKey) {
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new BusinessException("SOFTWARE_ACCESS_REQUEST_INVALID", "决定说明须为 1~500 字");
        }
        SoftwareAccessRequest request = requestMapper.selectById(applicationId);
        if (request == null) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "申请不存在");
        }
        String status = request.getStatus();
        if ("APPROVED".equals(status) || "REJECTED".equals(status)) {
            if (status.equals(approve ? "APPROVED" : "REJECTED")) {
                return true; // 终态重复同意图：幂等
            }
            throw new BusinessException("SOFTWARE_ACCESS_REQUEST_TERMINAL",
                    "申请已终态（" + status + "），不能改为其他结论");
        }
        if (!"PENDING".equals(status)) {
            throw new BusinessException("SOFTWARE_ACCESS_REQUEST_STATE", "申请已被撤回，不能审核");
        }
        // 审批不得替换申请人：授权对象恒为申请人本人
        Long grantId = null;
        if (approve) {
            AdminGrantRequest grantRequest = new AdminGrantRequest();
            grantRequest.setUserId(request.getApplicantId());
            grantRequest.setReason("访问申请通过：" + reason.trim());
            grantRequest.setExternalReference(request.getExternalReference());
            // 幂等键绑定申请 ID：同意图重试不重复授权
            boolean granted = adminSoftwarePermissionService.grant(adminId,
                    request.getSoftwareId(),
                    "access-request:" + applicationId, grantRequest);
            if (!granted) {
                throw new BusinessException("SOFTWARE_OPERATION_CONFLICT", "授权未生效，请刷新后重试");
            }
            // SW11/SF04：回写 grant_id——查申请人该软件最新授权（同事务可见）
            com.cloudmart.software.entity.SoftwareAccessGrant grant = accessGrantMapper.selectOne(
                    new LambdaQueryWrapper<com.cloudmart.software.entity.SoftwareAccessGrant>()
                            .eq(com.cloudmart.software.entity.SoftwareAccessGrant::getSoftwareId,
                                    request.getSoftwareId())
                            .eq(com.cloudmart.software.entity.SoftwareAccessGrant::getUserId,
                                    request.getApplicantId())
                            .orderByDesc(com.cloudmart.software.entity.SoftwareAccessGrant::getId)
                            .last("LIMIT 1"));
            grantId = grant == null ? null : grant.getId();
        }
        request.setStatus(approve ? "APPROVED" : "REJECTED");
        request.setHandledBy(adminId);
        request.setHandledAt(LocalDateTime.now(clock));
        request.setReason(reason.trim());
        request.setGrantId(grantId);
        requestMapper.updateById(request);
        SoftwareAuditLog audit = new SoftwareAuditLog();
        audit.setSoftwareId(request.getSoftwareId());
        audit.setActorId(adminId);
        audit.setActorType("ADMIN");
        audit.setAction(approve ? "ACCESS_REQUEST_APPROVE" : "ACCESS_REQUEST_REJECT");
        audit.setFromStatus(status);
        audit.setToStatus(request.getStatus());
        audit.setReason(reason.trim());
        audit.setCreatedAt(LocalDateTime.now(clock));
        auditLogMapper.insert(audit);
        return true;
    }

    private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SoftwareAccessRequest>
            pendingOf(Long userId, Long softwareId) {
        return new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SoftwareAccessRequest>()
                .eq(SoftwareAccessRequest::getApplicantId, userId)
                .eq(SoftwareAccessRequest::getSoftwareId, softwareId)
                .eq(SoftwareAccessRequest::getStatus, "PENDING")
                .orderByDesc(SoftwareAccessRequest::getId)
                .last("LIMIT 1");
    }

    private Map<String, Object> toVo(SoftwareAccessRequest request) {
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("requestId", String.valueOf(request.getId()));
        vo.put("softwareId", String.valueOf(request.getSoftwareId()));
        vo.put("applicantId", String.valueOf(request.getApplicantId()));
        vo.put("status", request.getStatus());
        vo.put("message", request.getMessage());
        // 证据/凭据仅授权员可见：用户视图不含 external_reference 与 evidence 内容
        vo.put("reason", request.getReason());
        vo.put("grantId", request.getGrantId() == null ? null : String.valueOf(request.getGrantId()));
        vo.put("createdAt", String.valueOf(request.getCreatedAt()));
        vo.put("handledAt", request.getHandledAt() == null ? null : String.valueOf(request.getHandledAt()));
        return vo;
    }

    private Page<Map<String, Object>> toPage(Page<SoftwareAccessRequest> result) {
        Page<Map<String, Object>> page = new Page<>(result.getCurrent(), result.getSize(),
                result.getTotal());
        page.setRecords(result.getRecords().stream().map(this::toVo).toList());
        return page;
    }
}
