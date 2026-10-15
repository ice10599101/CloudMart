package com.cloudmart.software.service;

import com.cloudmart.common.async.EventEnvelope;
import com.cloudmart.common.async.outbox.OutboxService;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareAuditLog;
import com.cloudmart.software.enums.SoftwareLifecycle;
import com.cloudmart.software.mq.SoftwareEvents;
import com.cloudmart.software.repository.SoftwareAuditLogMapper;
import com.cloudmart.software.repository.SoftwareMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 平台处置动作（方案 7.5/4.2/10.1）：封禁与解除的唯一实现，
 * 举报处置（BAN_SOFTWARE）与“软件管理”独立封禁共用，避免双份状态机。
 *
 * <p>不变量：封禁必须原因与版本（独立入口）；解除回到 UNLISTED，由作者恢复展示；
 * 状态变更、审计与 Outbox 通知同一软件库事务提交。</p>
 */
@Service
@RequiredArgsConstructor
public class SoftwarePlatformActionService {

    private final SoftwareMapper softwareMapper;
    private final SoftwareAuditLogMapper auditLogMapper;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * 平台封禁：BANNED（作者不可自行恢复）。
     *
     * @param expectedVersion 非空时做 CAS；举报处置路径不携带版本
     */
    @Transactional
    public boolean ban(Long adminId, Long softwareId, String reason, Integer expectedVersion) {
        Software software = softwareMapper.selectById(softwareId);
        if (software == null || software.getLifecycle() == SoftwareLifecycle.DELETED) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
        }
        if (software.getLifecycle() == SoftwareLifecycle.BANNED) {
            return false; // 幂等：已封禁重复调用无副作用
        }
        if (expectedVersion != null && !expectedVersion.equals(software.getLockVersion())) {
            throw new BusinessException("SOFTWARE_VERSION_CONFLICT", "软件已被其他操作更新，请刷新后重试");
        }
        SoftwareLifecycle from = software.getLifecycle();
        software.setLifecycle(SoftwareLifecycle.BANNED);
        software.setLockVersion(software.getLockVersion() + 1);
        softwareMapper.updateById(software);
        writeAudit(softwareId, adminId, "BAN", from.name(), "BANNED", reason);
        outboxService.record(EventEnvelope.of(SoftwareEvents.TAG_SOFTWARE_BANNED, 1,
                String.valueOf(softwareId), software.getLockVersion(), null,
                objectMapper.writeValueAsString(Map.of(
                        "softwareId", String.valueOf(softwareId),
                        "authorId", String.valueOf(software.getOwnerId()),
                        "reason", reason == null ? "" : reason))));
        return true;
    }

    /**
     * 平台解除：BANNED → UNLISTED（由作者恢复展示，不自动回 ACTIVE）。
     */
    @Transactional
    public boolean unban(Long adminId, Long softwareId, String reason, Integer expectedVersion) {
        Software software = softwareMapper.selectById(softwareId);
        if (software == null || software.getLifecycle() == SoftwareLifecycle.DELETED) {
            throw new BusinessException("SOFTWARE_NOT_FOUND", "软件不存在");
        }
        if (software.getLifecycle() != SoftwareLifecycle.BANNED) {
            throw new BusinessException("SOFTWARE_STATE_CONFLICT", "仅封禁状态可解除封禁");
        }
        if (expectedVersion != null && !expectedVersion.equals(software.getLockVersion())) {
            throw new BusinessException("SOFTWARE_VERSION_CONFLICT", "软件已被其他操作更新，请刷新后重试");
        }
        software.setLifecycle(SoftwareLifecycle.UNLISTED);
        software.setLockVersion(software.getLockVersion() + 1);
        softwareMapper.updateById(software);
        writeAudit(softwareId, adminId, "UNBAN", "BANNED", "UNLISTED", reason);
        return true;
    }

    private void writeAudit(Long softwareId, Long adminId, String action,
                            String from, String to, String reason) {
        SoftwareAuditLog logEntry = new SoftwareAuditLog();
        logEntry.setSoftwareId(softwareId);
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
