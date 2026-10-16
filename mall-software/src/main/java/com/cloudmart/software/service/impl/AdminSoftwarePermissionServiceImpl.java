package com.cloudmart.software.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.dto.software.AdminContactSaveRequest;
import com.cloudmart.software.dto.software.AdminGrantRequest;
import com.cloudmart.software.dto.software.AdminGrantRevokeRequest;
import com.cloudmart.software.dto.software.AdminPermissionToggleRequest;
import com.cloudmart.software.dto.software.SoftwareProfileVO;
import com.cloudmart.software.entity.SoftwareAccessContact;
import com.cloudmart.software.entity.SoftwareAccessGrant;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwarePermissionAudit;
import com.cloudmart.software.entity.SoftwarePublisherPermission;
import com.cloudmart.software.feign.UserSupportFeignClient;
import com.cloudmart.software.repository.SoftwareAccessContactMapper;
import com.cloudmart.software.repository.SoftwareAccessGrantMapper;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwarePermissionAuditMapper;
import com.cloudmart.software.repository.SoftwarePublisherPermissionMapper;
import com.cloudmart.software.repository.SoftwareLockMapper;
import com.cloudmart.software.service.AdminSoftwarePermissionService;
import com.cloudmart.software.service.SoftwareOperationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 资格/授权/联系入口管理实现（方案 4.0/6.14）。
 *
 * <p>不变量：资格与授权以数据库为最终事实；缺失记录视为拒绝；首开用唯一键
 * 幂等插入；权限变更、审计与 software_operation 同事务提交；开通前经用户服务
 * 核对会员存在且可用（查询失败 503，不创建未知用户授权）；锁顺序：资格行/授权行
 * →（涉及软件时）软件主行。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSoftwarePermissionServiceImpl implements AdminSoftwarePermissionService {

    private static final Set<String> CONTACT_TYPES = Set.of("SITE_USER", "TEXT", "HTTPS_URL");

    private final SoftwarePublisherPermissionMapper publisherPermissionMapper;
    private final SoftwareAccessGrantMapper accessGrantMapper;
    private final SoftwareAccessContactMapper accessContactMapper;
    private final SoftwarePermissionAuditMapper permissionAuditMapper;
    private final SoftwareLockMapper lockMapper;
    private final SoftwareMapper softwareMapper;
    private final UserSupportFeignClient userSupportFeignClient;
    private final SoftwareOperationService operationService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    // ------------------------------------------------------------------
    // 发布资格（4.0）
    // ------------------------------------------------------------------

    @Override
    public Page<Map<String, Object>> permissionPage(int page, int size) {
        Page<SoftwarePublisherPermission> permissionPage = publisherPermissionMapper.selectPage(
                Page.of(Math.max(page, 1), Math.min(Math.max(size, 1), 100)),
                new LambdaQueryWrapper<SoftwarePublisherPermission>()
                        .orderByDesc(SoftwarePublisherPermission::getUpdatedAt));
        Page<Map<String, Object>> result = new Page<>(permissionPage.getCurrent(),
                permissionPage.getSize(), permissionPage.getTotal());
        result.setRecords(permissionPage.getRecords().stream()
                .map(this::toPermissionRow)
                .toList());
        return result;
    }

    @Override
    public List<Map<String, Object>> permissionsOf(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return publisherPermissionMapper.selectList(new LambdaQueryWrapper<SoftwarePublisherPermission>()
                        .in(SoftwarePublisherPermission::getUserId, userIds))
                .stream().map(this::toPermissionRow)
                .toList();
    }

    private Map<String, Object> toPermissionRow(SoftwarePublisherPermission permission) {
        Map<String, Object> row = new HashMap<>();
        row.put("userId", String.valueOf(permission.getUserId()));
        row.put("canPublish", permission.getCanPublish());
        row.put("updatedBy", permission.getUpdatedBy() == null ? null
                : String.valueOf(permission.getUpdatedBy()));
        row.put("reason", permission.getReason());
        row.put("lockVersion", permission.getLockVersion());
        row.put("updatedAt", String.valueOf(permission.getUpdatedAt()));
        return row;
    }

    @Override
    @Transactional
    public boolean togglePermission(Long adminId, Long userId, String idempotencyKey,
                                     AdminPermissionToggleRequest request) {
        if (request.getEnable() == null) {
            throw new BusinessException("SOFTWARE_PERMISSION_INVALID", "enable 不能为空");
        }
        if (request.getReason() == null || request.getReason().isBlank()
                || request.getReason().length() > 500) {
            throw new BusinessException("SOFTWARE_PERMISSION_INVALID", "原因须为 1~500 字");
        }
        if (userId == null || userId <= 0) {
            throw new BusinessException("SOFTWARE_PERMISSION_INVALID", "userId 非法");
        }
        // 开通先核对会员存在且可用（4.0：查询失败 503，不创建未知用户授权）
        var profiles = userSupportFeignClient.profiles(Map.of("userIds", List.of(userId))).data();
        if (profiles == null || profiles.isEmpty()) {
            throw new BusinessException("USER_SERVICE_UNAVAILABLE", "会员状态不可用，请稍后重试");
        }
        if (!profiles.get(0).accountAvailable()) {
            throw new BusinessException("ACCOUNT_UNAVAILABLE", "会员账号状态异常，不能变更发布资格");
        }
        String before = null;
        // 事务内锁资格行（唯一键首开幂等；缺失记录视为未开通）
        SoftwarePublisherPermission permission = lockMapper.lockPublisherByUser(userId);
        boolean enabling = request.getEnable();
        if (permission == null) {
            if (!enabling) {
                throw new BusinessException("SOFTWARE_PERMISSION_INVALID",
                        "该会员尚无发布资格记录，无需关闭");
            }
            before = null;
        } else {
            before = toSnapshotJson(snapshotMap("canPublish", permission.getCanPublish()));
        }
        String beforeSnapshot = before;
        operationService.execute("ADMIN", adminId, SoftwareOperationService.OP_PERMISSION_TOGGLE,
                idempotencyKey,
                Map.of("userId", userId, "enable", enabling, "reason", request.getReason()),
                null,
                (Supplier<Void>) () -> {
                    doToggle(adminId, userId, enabling, request.getReason(), beforeSnapshot);
                    return null;
                },
                json -> null);
        return true;
    }

    private void doToggle(Long adminId, Long userId, boolean enable, String reason, String before) {
        SoftwarePublisherPermission permission = publisherPermissionMapper.selectOne(
                new LambdaQueryWrapper<SoftwarePublisherPermission>()
                        .eq(SoftwarePublisherPermission::getUserId, userId));
        String after = toSnapshotJson(snapshotMap("canPublish", enable));
        if (permission == null) {
            // 唯一键首开（4.0）；并发首开冲突按冲突拒绝（客户端重试命中幂等结果）
            permission = new SoftwarePublisherPermission();
            permission.setUserId(userId);
            permission.setCanPublish(enable);
            permission.setUpdatedBy(adminId);
            permission.setReason(reason);
            permission.setLockVersion(0);
            try {
                publisherPermissionMapper.insert(permission);
            } catch (DuplicateKeyException e) {
                throw new BusinessException("SOFTWARE_OPERATION_CONFLICT",
                        "资格记录已存在，请刷新后重试");
            }
        } else {
            permission.setCanPublish(enable);
            permission.setUpdatedBy(adminId);
            permission.setReason(reason);
            permission.setLockVersion(permission.getLockVersion() + 1);
            publisherPermissionMapper.updateById(permission);
        }
        writePermissionAudit(null, null, null, adminId, enable
                        ? "PUBLISH_ENABLE" : "PUBLISH_DISABLE",
                before, after, reason, null);
    }

    private void writePermissionAudit(Long targetUserId, Long softwareId, Long contactId,
                                      Long actorAdminId, String action, String before,
                                      String after, String reason, String requestId) {
        SoftwarePermissionAudit audit = new SoftwarePermissionAudit();
        audit.setTargetUserId(targetUserId);
        audit.setSoftwareId(softwareId);
        audit.setContactId(contactId);
        audit.setActorAdminId(actorAdminId);
        audit.setAction(action);
        audit.setBeforeJson(before);
        audit.setAfterJson(after);
        audit.setReason(reason);
        audit.setRequestId(requestId);
        audit.setCreatedAt(LocalDateTime.now(clock));
        permissionAuditMapper.insert(audit);
    }

    /**
     * 构建允许 null 值的快照容器（Map.of 遇 null 抛 NPE，故显式 HashMap）。
     * 入参按 key/value 成对给出。
     */
    private Map<String, Object> snapshotMap(Object... keyValues) {
        Map<String, Object> payload = new HashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            payload.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return payload;
    }

    /**
     * 权限审计快照序列化（4.0/6.14）：before_json / after_json 为 MySQL json 列，
     * 必须是合法 JSON 文本——早期实现用字符串拼接（"ACTIVE;expiresAt=..."）在
     * MySQL 严格模式下触发 Data truncation: Invalid JSON text，导致整个管理端
     * 写操作 500。此处统一按项目既有约定走 ObjectMapper。
     *
     * <p>用 HashMap 而非 Map.of：快照值允许 null（如生效前状态），Map.of 遇 null 抛 NPE。
     */
    private String toSnapshotJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            throw new IllegalStateException("权限审计快照序列化失败", e);
        }
    }

    // ------------------------------------------------------------------
    // 付款查看授权（5.4/6.14）
    // ------------------------------------------------------------------

    @Override
    public List<Map<String, Object>> grantsOf(Long softwareId) {
        if (softwareMapper.selectById(softwareId) == null) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
        }
        return accessGrantMapper.selectList(new LambdaQueryWrapper<SoftwareAccessGrant>()
                        .eq(SoftwareAccessGrant::getSoftwareId, softwareId)
                        .orderByDesc(SoftwareAccessGrant::getGrantedAt))
                .stream().map(grant -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("grantId", String.valueOf(grant.getId()));
                    row.put("userId", String.valueOf(grant.getUserId()));
                    row.put("status", grant.getStatus());
                    row.put("grantedBy", grant.getGrantedBy() == null ? null
                            : String.valueOf(grant.getGrantedBy()));
                    row.put("grantedAt", String.valueOf(grant.getGrantedAt()));
                    row.put("expiresAt", String.valueOf(grant.getExpiresAt()));
                    row.put("confirmationNote", grant.getConfirmationNote());
                    row.put("externalReference", grant.getExternalReference());
                    row.put("lockVersion", grant.getLockVersion());
                    return row;
                })
                .toList();
    }

    @Override
    public Page<Map<String, Object>> grantPage(Long softwareId, Long userId, int page, int size) {
        Page<SoftwareAccessGrant> grantRows = accessGrantMapper.selectPage(
                Page.of(Math.max(page, 1), Math.min(Math.max(size, 1), 100)),
                new LambdaQueryWrapper<SoftwareAccessGrant>()
                        .eq(softwareId != null, SoftwareAccessGrant::getSoftwareId, softwareId)
                        .eq(userId != null, SoftwareAccessGrant::getUserId, userId)
                        .orderByDesc(SoftwareAccessGrant::getGrantedAt)
                        .orderByDesc(SoftwareAccessGrant::getId));
        LocalDateTime now = LocalDateTime.now(clock);
        Page<Map<String, Object>> result = new Page<>(grantRows.getCurrent(), grantRows.getSize(),
                grantRows.getTotal());
        result.setRecords(grantRows.getRecords().stream().map(grant -> {
            Map<String, Object> row = new HashMap<>();
            row.put("grantId", String.valueOf(grant.getId()));
            row.put("softwareId", String.valueOf(grant.getSoftwareId()));
            row.put("userId", String.valueOf(grant.getUserId()));
            // 展示口径：撤销优先，其次按当前时间实时判定到期（与授权裁决一致）
            row.put("status", effectiveGrantStatus(grant, now));
            row.put("grantedBy", grant.getGrantedBy() == null ? null
                    : String.valueOf(grant.getGrantedBy()));
            row.put("grantedAt", String.valueOf(grant.getGrantedAt()));
            row.put("expiresAt", grant.getExpiresAt() == null ? null
                    : String.valueOf(grant.getExpiresAt()));
            row.put("confirmationNote", grant.getConfirmationNote());
            row.put("externalReference", grant.getExternalReference());
            row.put("lockVersion", grant.getLockVersion());
            return row;
        }).toList());
        return result;
    }

    private String effectiveGrantStatus(SoftwareAccessGrant grant, LocalDateTime now) {
        if ("REVOKED".equals(grant.getStatus())) {
            return "REVOKED";
        }
        if (grant.getExpiresAt() != null && !grant.getExpiresAt().isAfter(now)) {
            return "EXPIRED";
        }
        return "ACTIVE";
    }

    @Override
    public Page<Map<String, Object>> auditPage(Long userId, Long softwareId, int page, int size) {
        Page<SoftwarePermissionAudit> auditRows = permissionAuditMapper.selectPage(
                Page.of(Math.max(page, 1), Math.min(Math.max(size, 1), 100)),
                new LambdaQueryWrapper<SoftwarePermissionAudit>()
                        .eq(userId != null, SoftwarePermissionAudit::getTargetUserId, userId)
                        .eq(softwareId != null, SoftwarePermissionAudit::getSoftwareId, softwareId)
                        .orderByDesc(SoftwarePermissionAudit::getId));
        Page<Map<String, Object>> result = new Page<>(auditRows.getCurrent(), auditRows.getSize(),
                auditRows.getTotal());
        result.setRecords(auditRows.getRecords().stream().map(audit -> {
            Map<String, Object> row = new HashMap<>();
            row.put("auditId", String.valueOf(audit.getId()));
            row.put("targetUserId", audit.getTargetUserId() == null ? null
                    : String.valueOf(audit.getTargetUserId()));
            row.put("softwareId", audit.getSoftwareId() == null ? null
                    : String.valueOf(audit.getSoftwareId()));
            row.put("contactId", audit.getContactId() == null ? null
                    : String.valueOf(audit.getContactId()));
            row.put("actorAdminId", audit.getActorAdminId() == null ? null
                    : String.valueOf(audit.getActorAdminId()));
            row.put("action", audit.getAction());
            row.put("beforeJson", audit.getBeforeJson());
            row.put("afterJson", audit.getAfterJson());
            row.put("reason", audit.getReason());
            row.put("requestId", audit.getRequestId());
            row.put("createdAt", String.valueOf(audit.getCreatedAt()));
            return row;
        }).toList());
        return result;
    }

    @Override
    @Transactional
    public boolean grant(Long adminId, Long softwareId, String idempotencyKey,
                          AdminGrantRequest request) {
        if (request.getUserId() == null || request.getUserId() <= 0) {
            throw new BusinessException("SOFTWARE_ACCESS_INVALID", "userId 非法");
        }
        if (request.getReason() == null || request.getReason().isBlank()
                || request.getReason().length() > 500) {
            throw new BusinessException("SOFTWARE_ACCESS_INVALID", "原因须为 1~500 字");
        }
        // 开通先核对会员存在且可用（4.0 同规则）
        var profiles = userSupportFeignClient.profiles(
                Map.of("userIds", List.of(request.getUserId()))).data();
        if (profiles == null || profiles.isEmpty()) {
            throw new BusinessException("USER_SERVICE_UNAVAILABLE", "会员状态不可用，请稍后重试");
        }
        if (!profiles.get(0).accountAvailable()) {
            throw new BusinessException("ACCOUNT_UNAVAILABLE", "会员账号状态异常，不能开通授权");
        }
        if (softwareMapper.selectById(softwareId) == null) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
        }
        operationService.execute("ADMIN", adminId, SoftwareOperationService.OP_ACCESS_GRANT,
                idempotencyKey,
                Map.of("softwareId", softwareId, "userId", request.getUserId(),
                        "expiresAt", String.valueOf(request.getExpiresAt()),
                        "reason", request.getReason()),
                softwareId,
                (Supplier<Void>) () -> {
                    doGrant(adminId, softwareId, request);
                    return null;
                },
                json -> null);
        return true;
    }

    private void doGrant(Long adminId, Long softwareId, AdminGrantRequest request) {
        Software software = lockMapper.lockSoftware(softwareId);
        if (software == null) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
        }
        // 重开/延期更新同一行并追加审计（6.14）；锁授权行保证并发安全
        SoftwareAccessGrant grant = lockMapper.lockAccessGrant(softwareId, request.getUserId());
        boolean isNew = grant == null;
        if (isNew) {
            grant = new SoftwareAccessGrant();
            grant.setSoftwareId(softwareId);
            grant.setUserId(request.getUserId());
            grant.setStatus("ACTIVE");
            grant.setGrantedBy(adminId);
            grant.setGrantedAt(LocalDateTime.now(clock));
            grant.setLockVersion(0);
        }
        grant.setExpiresAt(request.getExpiresAt());
        grant.setConfirmationNote(request.getConfirmationNote());
        grant.setExternalReference(request.getExternalReference());
        grant.setLockVersion(grant.getLockVersion() + 1);
        if (isNew) {
            try {
                accessGrantMapper.insert(grant);
            } catch (DuplicateKeyException e) {
                throw new BusinessException("SOFTWARE_OPERATION_CONFLICT",
                        "授权记录已存在，请刷新后重试");
            }
        } else {
            // 重开：REVOKED → ACTIVE（撤销后重新授权更新同一行，6.14）
            grant.setStatus("ACTIVE");
            accessGrantMapper.updateById(grant);
        }
        writePermissionAudit(request.getUserId(), softwareId, null, adminId,
                isNew ? "ACCESS_GRANT" : "ACCESS_EXTEND", null,
                toSnapshotJson(snapshotMap("status", "ACTIVE",
                        "expiresAt", request.getExpiresAt())),
                request.getReason(), null);
    }

    @Override
    @Transactional
    public boolean revoke(Long adminId, Long softwareId, Long userId, String idempotencyKey,
                           AdminGrantRevokeRequest request) {
        if (request.getReason() == null || request.getReason().isBlank()
                || request.getReason().length() > 500) {
            throw new BusinessException("SOFTWARE_ACCESS_INVALID", "原因须为 1~500 字");
        }
        operationService.execute("ADMIN", adminId, SoftwareOperationService.OP_ACCESS_REVOKE,
                idempotencyKey,
                Map.of("softwareId", softwareId, "userId", userId, "reason", request.getReason()),
                softwareId,
                (Supplier<Void>) () -> {
                    doRevoke(adminId, softwareId, userId, request.getReason());
                    return null;
                },
                json -> null);
        return true;
    }

    private void doRevoke(Long adminId, Long softwareId, Long userId, String reason) {
        SoftwareAccessGrant grant = lockMapper.lockAccessGrant(softwareId, userId);
        if (grant == null || "REVOKED".equals(grant.getStatus())) {
            return; // 幂等：无授权或已撤销
        }
        grant.setStatus("REVOKED");
        grant.setRevokedBy(adminId);
        grant.setRevokedAt(LocalDateTime.now(clock));
        grant.setLockVersion(grant.getLockVersion() + 1);
        accessGrantMapper.updateById(grant);
        writePermissionAudit(userId, softwareId, null, adminId, "ACCESS_REVOKE",
                toSnapshotJson(snapshotMap("status", "ACTIVE")),
                toSnapshotJson(snapshotMap("status", "REVOKED")), reason, null);
    }

    // ------------------------------------------------------------------
    // 联系入口（5.4/6.14）
    // ------------------------------------------------------------------

    @Override
    public List<Map<String, Object>> contacts() {
        return accessContactMapper.selectList(new LambdaQueryWrapper<SoftwareAccessContact>()
                        .orderByAsc(SoftwareAccessContact::getSortOrder)
                        .orderByAsc(SoftwareAccessContact::getId))
                .stream().map(contact -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", String.valueOf(contact.getId()));
                    row.put("name", contact.getName());
                    row.put("contactType", contact.getContactType());
                    row.put("contactValue", contact.getContactValue());
                    row.put("enabled", contact.getEnabled());
                    row.put("sortOrder", contact.getSortOrder());
                    row.put("lockVersion", contact.getLockVersion());
                    return row;
                })
                .toList();
    }

    @Override
    @Transactional
    public Long saveContact(Long adminId, String idempotencyKey, AdminContactSaveRequest request) {
        if (request.getContactType() == null
                || !CONTACT_TYPES.contains(request.getContactType())) {
            throw new BusinessException("SOFTWARE_CONTACT_INVALID",
                    "contactType 须为 SITE_USER/TEXT/HTTPS_URL");
        }
        if (request.getName() == null || request.getName().isBlank()
                || request.getName().length() > 80) {
            throw new BusinessException("SOFTWARE_CONTACT_INVALID", "名称须为 1~80 字");
        }
        if (request.getContactValue() == null || request.getContactValue().isBlank()
                || request.getContactValue().length() > 500) {
            throw new BusinessException("SOFTWARE_CONTACT_INVALID", "联系内容须为 1~500 字");
        }
        // HTTPS_URL 拒绝危险协议（6.14：内容由管理员维护，拒绝秘密信息和危险协议）
        if ("HTTPS_URL".equals(request.getContactType())
                && !request.getContactValue().trim().startsWith("https://")) {
            throw new BusinessException("SOFTWARE_CONTACT_INVALID", "HTTPS_URL 类型须以 https:// 开头");
        }
        operationService.execute("ADMIN", adminId, SoftwareOperationService.OP_CONTACT_SAVE,
                idempotencyKey,
                Map.of("id", String.valueOf(request.getId()), "name", request.getName(),
                        "contactType", request.getContactType(),
                        "contactValue", request.getContactValue(),
                        "enabled", String.valueOf(request.getEnabled()),
                        "sortOrder", String.valueOf(request.getSortOrder())),
                null,
                (Supplier<Void>) () -> {
                    doSaveContact(adminId, request);
                    return null;
                },
                json -> null);
        return request.getId();
    }

    private void doSaveContact(Long adminId, AdminContactSaveRequest request) {
        SoftwareAccessContact contact;
        boolean isNew = request.getId() == null;
        if (isNew) {
            contact = new SoftwareAccessContact();
            contact.setEnabled(true);
            contact.setSortOrder(0);
            contact.setLockVersion(0);
        } else {
            contact = accessContactMapper.selectById(request.getId());
            if (contact == null) {
                throw new BusinessException("SOFTWARE_NOT_FOUND", "联系入口不存在");
            }
            // 引用中仅允许停用，不物理删除（6.14）
            contact.setLockVersion(contact.getLockVersion() + 1);
        }
        contact.setName(request.getName().trim());
        contact.setContactType(request.getContactType());
        contact.setContactValue(request.getContactValue().trim());
        if (request.getEnabled() != null) {
            contact.setEnabled(request.getEnabled());
        }
        if (request.getSortOrder() != null) {
            contact.setSortOrder(request.getSortOrder());
        }
        contact.setUpdatedBy(adminId);
        if (isNew) {
            accessContactMapper.insert(contact);
            request.setId(contact.getId());
        } else {
            accessContactMapper.updateById(contact);
        }
        writePermissionAudit(null, null, contact.getId(), adminId, "CONTACT_UPDATE",
                null, toSnapshotJson(snapshotMap("contactType", contact.getContactType(),
                        "contactValue", contact.getContactValue(),
                        "enabled", contact.getEnabled())),
                request.getReason(), null);
    }
}
