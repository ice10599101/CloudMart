package com.cloudmart.software.service;

import com.cloudmart.software.entity.SoftwareAuditLog;
import com.cloudmart.software.repository.SoftwareAuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 软件域审核与状态流转日志（方案 6.10）：只追加，不覆盖历史原因；
 * 不记录下载 URL/密码。与业务变更同事务调用。
 */
@Service
@RequiredArgsConstructor
public class SoftwareAuditService {

    private final SoftwareAuditLogMapper auditLogMapper;

    public void record(Long softwareId, Long revisionId, Integer submitSeq, Long actorId,
                       String actorType, String action, String fromStatus, String toStatus,
                       String reason, String requestId) {
        SoftwareAuditLog log = new SoftwareAuditLog();
        log.setSoftwareId(softwareId);
        log.setRevisionId(revisionId);
        log.setSubmitSeq(submitSeq);
        log.setActorId(actorId);
        log.setActorType(actorType);
        log.setAction(action);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setReason(reason);
        log.setRequestId(requestId);
        auditLogMapper.insert(log);
    }
}
