package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.pet.entity.PetOperation;
import com.cloudmart.pet.repository.PetOperationMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 业务操作记录存储（B01）：操作行的持久化与状态流转。
 *
 * <p>{@link #claim} 以 REQUIRES_NEW 先行提交 PENDING 行——操作记录必须先于不可回滚的
 * 远程调用持久化，调用方本地事务随后回滚不能连带抹掉操作记录（否则远程结果将无法追溯）。
 * 结果回写（{@link #markCompleted}/{@link #markTerminal}）加入调用方当前事务：
 * 本地业务与操作状态同生共死，回滚则退回 PENDING 由恢复任务兜底。</p>
 */
@Component
@Slf4j
public class PetOperationStore {

    private final PetOperationMapper operationMapper;

    public PetOperationStore(PetOperationMapper operationMapper) {
        this.operationMapper = operationMapper;
    }

    public PetOperation findByOperationId(String operationId) {
        return operationMapper.selectOne(new LambdaQueryWrapper<PetOperation>()
                .eq(PetOperation::getOperationId, operationId));
    }

    /** 恢复任务的扫描入口：PENDING/UNKNOWN 且到达退避时间的操作，批量有序推进 */
    public List<PetOperation> listRecoverable(int limit) {
        return operationMapper.selectList(new LambdaQueryWrapper<PetOperation>()
                .in(PetOperation::getStatus, "PENDING", "UNKNOWN")
                .and(w -> w.isNull(PetOperation::getNextRetryAt)
                        .or().le(PetOperation::getNextRetryAt, LocalDateTime.now(ZoneOffset.UTC)))
                .orderByAsc(PetOperation::getNextRetryAt)
                .orderByAsc(PetOperation::getId)
                .last("LIMIT " + limit));
    }

    /**
     * 占住操作键（REQUIRES_NEW 独立提交）。重复请求命中已有行时校验业务内容一致，
     * 不一致抛 {@code PET_OPERATION_CONFLICT}（同键不同内容必须拒绝）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public PetOperation claim(String operationId, Long userId, Long petId, String bizType,
                              Long bizRefId, String direction, int amount, String rewardSnapshot) {
        PetOperation fresh = new PetOperation();
        fresh.setOperationId(operationId);
        fresh.setUserId(userId);
        fresh.setPetId(petId);
        fresh.setBizType(bizType);
        fresh.setBizRefId(bizRefId);
        fresh.setDirection(direction);
        fresh.setAmount(amount);
        fresh.setStatus("PENDING");
        fresh.setRetryCount(0);
        fresh.setRewardSnapshot(rewardSnapshot);
        try {
            operationMapper.insert(fresh);
            return fresh;
        } catch (DuplicateKeyException duplicate) {
            PetOperation existing = findByOperationId(operationId);
            if (existing == null) {
                // 唯一键冲突但行不可读：并发事务尚未提交，按"处理中"处理，恢复任务兜底
                throw new PetOperationPendingException();
            }
            boolean same = existing.getUserId().equals(userId)
                    && existing.getBizType().equals(bizType)
                    && existing.getDirection().equals(direction)
                    && existing.getAmount().equals(amount)
                    && (existing.getPetId() == null ? petId == null : existing.getPetId().equals(petId))
                    && (existing.getBizRefId() == null ? bizRefId == null : existing.getBizRefId().equals(bizRefId));
            if (!same) {
                throw new com.cloudmart.common.exception.BusinessException(
                        com.cloudmart.pet.constant.PetErrorCodes.PET_OPERATION_CONFLICT,
                        "操作键已存在但业务内容不同，禁止复用该键");
            }
            return existing;
        }
    }

    /** 加入当前事务的结果回写：本地业务回滚时状态回退 PENDING */
    public void markCompleted(PetOperation operation, String walletResultJson) {
        operation.setStatus("COMPLETED");
        operation.setWalletResult(walletResultJson);
        operation.setCompletedAt(LocalDateTime.now(ZoneOffset.UTC));
        operationMapper.updateById(operation);
    }

    /** FAILED/UNKNOWN 状态回写（加入当前事务）；UNKNOWN 携带退避时间供恢复任务调度 */
    public void markTerminal(PetOperation operation, String status, String error, LocalDateTime nextRetryAt) {
        operation.setStatus(status);
        operation.setLastError(truncate(error));
        if ("FAILED".equals(status)) {
            operation.setCompletedAt(LocalDateTime.now(ZoneOffset.UTC));
        } else {
            operation.setNextRetryAt(nextRetryAt);
        }
        operationMapper.updateById(operation);
    }

    /** 恢复任务专用：增加重试计数（独立小事务，不牵连业务表） */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markRetry(PetOperation operation, String status, String walletResultJson,
                          String error, LocalDateTime nextRetryAt) {
        operation.setStatus(status);
        operation.setRetryCount(operation.getRetryCount() == null ? 1 : operation.getRetryCount() + 1);
        operation.setWalletResult(walletResultJson);
        operation.setLastError(truncate(error));
        if ("COMPLETED".equals(status) || "COMPENSATED".equals(status)) {
            operation.setCompletedAt(LocalDateTime.now(ZoneOffset.UTC));
        } else {
            operation.setNextRetryAt(nextRetryAt);
        }
        operationMapper.updateById(operation);
    }

    /** 防止恢复任务与业务线程并发写同一行：按状态条件 CAS，0 行说明状态已被他人推进 */
    public boolean casStatus(PetOperation operation, String fromStatus, String toStatus) {
        int updated = operationMapper.update(null, new LambdaUpdateWrapper<PetOperation>()
                .set(PetOperation::getStatus, toStatus)
                .eq(PetOperation::getId, operation.getId())
                .eq(PetOperation::getStatus, fromStatus));
        if (updated > 0) {
            operation.setStatus(toStatus);
        }
        return updated > 0;
    }

    private String truncate(String error) {
        return error != null && error.length() > 480 ? error.substring(0, 480) : error;
    }

    /** 操作处理中（并发同请求尚未提交结果） */
    public static class PetOperationPendingException extends RuntimeException {
    }
}
