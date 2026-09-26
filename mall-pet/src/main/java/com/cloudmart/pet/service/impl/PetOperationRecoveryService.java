package com.cloudmart.pet.service.impl;

import com.cloudmart.pet.entity.PetOperation;
import com.cloudmart.pet.feign.WishFeignClient;
import com.cloudmart.pet.feign.WishFeignClient.PetWalletOperationVO;
import com.cloudmart.pet.service.PetOperationRecoverable;
import com.cloudmart.pet.util.PetJsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 待结算操作恢复任务（B01）：对 PENDING/UNKNOWN 的业务操作按原单收敛，
 * 禁止换单号二次交易。
 *
 * <p>收敛规则：</p>
 * <ul>
 *   <li>钱包可查到原单结果——EARN 直接标 COMPLETED（本地奖励早已提交）；
 *       SPEND 触发本地效果续投递（{@link PetOperationRecoverable} 幂等），
 *       本地永久无法履约则按原单号派生唯一补偿单退款（COMPENSATING→COMPENSATED，
 *       退款复用钱包幂等）。</li>
 *   <li>钱包查不到——按原单幂等重试远程调用（有限次数+指数退避），始终可查询，
 *       不会永久停留在未知状态且无人可处理（管理员重试接口见 B21）。</li>
 * </ul>
 */
@Component
@Slf4j
public class PetOperationRecoveryService {

    /** 自动幂等重试上限；超过后保持 UNKNOWN + 长退避，等待管理员介入（可查询、可重试） */
    private static final int MAX_AUTO_RETRY = 3;
    private static final long BACKOFF_BASE_SECONDS = 60;
    private static final long BACKOFF_MAX_SECONDS = 1800;
    private static final int BATCH_SIZE = 50;

    private final PetOperationStore operationStore;
    private final PetOperationService operationService;
    private final WishFeignClient wishFeignClient;
    private final Map<String, PetOperationRecoverable> recoverablesByBizType;

    public PetOperationRecoveryService(PetOperationStore operationStore,
                                       PetOperationService operationService,
                                       WishFeignClient wishFeignClient,
                                       List<PetOperationRecoverable> recoverables) {
        this.operationStore = operationStore;
        this.operationService = operationService;
        this.wishFeignClient = wishFeignClient;
        this.recoverablesByBizType = new HashMap<>();
        for (PetOperationRecoverable recoverable : recoverables) {
            recoverablesByBizType.put(recoverable.supportedBizType(), recoverable);
        }
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 30000)
    public void recoverPendingOperations() {
        List<PetOperation> operations = operationStore.listRecoverable(BATCH_SIZE);
        for (PetOperation operation : operations) {
            try {
                recoverOne(operation);
            } catch (Exception e) {
                log.error("操作恢复失败（下一轮退避重试）, operationId={}, bizType={}",
                        operation.getOperationId(), operation.getBizType(), e);
                scheduleRetry(operation, null);
            }
        }
    }

    private void recoverOne(PetOperation operation) {
        PetWalletOperationVO walletResult = operationService.queryWallet(operation.getOperationId());
        if (walletResult != null && "COMPLETED".equals(walletResult.status())) {
            settleWithWalletResult(operation, walletResult);
            return;
        }
        if (operation.getRetryCount() != null && operation.getRetryCount() >= MAX_AUTO_RETRY) {
            // 保持 UNKNOWN + 长退避：状态可查询，管理员可经 B21 接口按原单重试
            scheduleRetry(operation, null);
            log.warn("操作超过自动重试上限，等待人工处理, operationId={}, retryCount={}",
                    operation.getOperationId(), operation.getRetryCount());
            return;
        }
        retryRemote(operation);
    }

    /** 钱包已有结果：EARN 直接结算；SPEND 先补投递本地效果，无法履约走补偿退款 */
    private void settleWithWalletResult(PetOperation operation, PetWalletOperationVO walletResult) {
        if ("EARN".equals(operation.getDirection())) {
            operationStore.markRetry(operation, "COMPLETED", PetJsonUtils.toJson(walletResult), null, null);
            log.info("发放操作按原单结算完成, operationId={}, credited={}",
                    operation.getOperationId(), walletResult.creditedAmount());
            return;
        }
        PetOperationRecoverable recoverable = recoverablesByBizType.get(operation.getBizType());
        if (recoverable == null) {
            compensate(operation);
            return;
        }
        boolean applied;
        try {
            applied = recoverable.completePendingOperation(operation);
        } catch (Exception e) {
            // 本地效果补投递异常：按未知继续退避，不轻易退款（用户资产优先保护）
            scheduleRetry(operation, "本地效果补投递异常: " + e.getMessage());
            return;
        }
        if (applied) {
            operationStore.markRetry(operation, "COMPLETED", PetJsonUtils.toJson(walletResult), null, null);
            log.info("扣款操作按原单补齐本地效果, operationId={}", operation.getOperationId());
        } else {
            compensate(operation);
        }
    }

    /** 按原单号幂等重试远程调用（钱包端去重保证最多生效一次） */
    private void retryRemote(PetOperation operation) {
        try {
            PetWalletOperationVO result = ("SPEND".equals(operation.getDirection())
                    ? wishFeignClient.spendStarlightIdempotent(operation.getUserId(), operation.getAmount(),
                            operation.getBizRefId(), operation.getOperationId()).data()
                    : wishFeignClient.earnStarlightIdempotent(operation.getUserId(), operation.getAmount(),
                            operation.getBizRefId(), operation.getOperationId()).data());
            operationStore.markRetry(operation, "COMPLETED", PetJsonUtils.toJson(result), null, null);
        } catch (com.cloudmart.common.exception.BusinessException e) {
            // 明确失败（余额不足/内容冲突）：终态 FAILED，不再重试
            operationStore.markRetry(operation, "FAILED", null, e.getMessage(), null);
        } catch (Exception e) {
            scheduleRetry(operation, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** 补偿退款：操作键 = 原单:refund，复用钱包幂等；成功前原单保持 COMPENSATING 可追溯 */
    private void compensate(PetOperation operation) {
        operationStore.casStatus(operation, operation.getStatus(), "COMPENSATING");
        String refundOperationId = operationService.refundOperationId(operation.getOperationId());
        try {
            PetWalletOperationVO result = wishFeignClient.earnStarlightIdempotent(operation.getUserId(),
                    operation.getAmount(), operation.getBizRefId(), refundOperationId).data();
            operationStore.markRetry(operation, "COMPENSATED", PetJsonUtils.toJson(result),
                    "本地无法履约，已按原单退款", null);
            log.info("补偿退款完成, operationId={}, refundOperationId={}",
                    operation.getOperationId(), refundOperationId);
        } catch (com.cloudmart.common.exception.BusinessException e) {
            operationStore.markRetry(operation, "COMPENSATING", null,
                    "退款失败: " + e.getMessage(), backoffAt(operation.getRetryCount()));
        } catch (Exception e) {
            scheduleRetry(operation, "退款结果未知: " + e.getMessage());
        }
    }

    private void scheduleRetry(PetOperation operation, String error) {
        int nextCount = (operation.getRetryCount() == null ? 0 : operation.getRetryCount()) + 1;
        operationStore.markRetry(operation, "UNKNOWN", null, error, backoffAt(nextCount));
    }

    private LocalDateTime backoffAt(Integer retryCount) {
        int count = retryCount == null ? 1 : retryCount;
        long backoff = Math.min(BACKOFF_BASE_SECONDS * (1L << Math.min(count, 5)), BACKOFF_MAX_SECONDS);
        return LocalDateTime.now(ZoneOffset.UTC).plusSeconds(backoff);
    }
}
