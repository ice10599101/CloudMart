package com.cloudmart.software.service.impl;

import com.cloudmart.software.entity.SoftwareAuditLog;
import com.cloudmart.software.enums.SoftwareCommentStatus;
import com.cloudmart.software.repository.SoftwareAuditLogMapper;
import com.cloudmart.software.repository.SoftwareCommentMapper;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.service.SoftwareCommentStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 评论状态迁移实现（SW09）：带原状态条件的原子 UPDATE 是唯一写入路径，
 * "读取→判断→写"的竞态在此消除；计数与审计只在真正迁移后发生。
 */
@Service
@RequiredArgsConstructor
public class SoftwareCommentStatusServiceImpl implements SoftwareCommentStatusService {

    private final SoftwareCommentMapper commentMapper;
    private final SoftwareMapper softwareMapper;
    private final SoftwareAuditLogMapper auditLogMapper;
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean transition(Long softwareId, Long commentId, SoftwareCommentStatus from,
                              SoftwareCommentStatus to, int countDelta, Long adminId,
                              String auditAction, String reason) {
        int updated = commentMapper.transitionIfFrom(commentId, from.name(), to.name());
        if (updated == 0) {
            return false;
        }
        if (countDelta != 0) {
            softwareMapper.incrementCommentCount(softwareId, countDelta);
        }
        writeAudit(softwareId, commentId, adminId, auditAction, from.name(), to.name(), reason);
        return true;
    }

    private void writeAudit(Long softwareId, Long commentId, Long adminId, String action,
                            String from, String to, String reason) {
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
