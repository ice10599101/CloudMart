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
 * 待结算操作恢复任务（B01/P02）：对可恢复状态的业务操作按原单收敛，禁止换单号二次交易。
 *
 * <p>状态分支（P02/TX-03/TX-04）：</p>
 * <ul>
 *   <li><b>COMPENSATING（退款分支）</b>：只继续退款收敛，绝不重新进入发放/扣款分支；
 *       退款结果未知保持 COMPENSATING 退避重试。</li>
 *   <li><b>PENDING/UNKNOWN/PROCESSING（租约过期）</b>：先查钱包原单结果——
 *       SPEND 已扣：补投递本地效果（{@link PetOperationRecoverable}），无法履约走退款；
 *       EARN 已发：仅当原始状态为 UNKNOWN（本地奖励按"结算中"契约已提交）才标完成，
 *       PENDING 表示本地业务事务未提交、事实无法自动判定 → MANUAL_REVIEW，禁止猜测补发。</li>
 *   <li>钱包查不到：SPEND 按原单幂等重试（重试成功后仍补投递本地效果）；EARN 重试成功后
 *       仅 UNKNOWN 原始状态可直接完成，PENDING 转人工核查。</li>
 * </ul>
 *
 * <p>多实例并发：处理前先抢租约（{@link PetOperationStore#acquireLease}，CAS 到 PROCESSING），
 * 未抢到放弃本行；租约过期可接管。全部状态推进走 CAS/条件更新，0 行即退出。</p>
 */
@Component
@Slf4j
public class PetOperationRecoveryService {

    /** 自动幂等重试上限；超过后保持 UNKNOWN + 长退避，等待管理员介入（可查询、可重试） */
    private static final int MAX_AUTO_RETRY = 3;
    private static final long BACKOFF_BASE_SECONDS = 60;
    private static final long BACKOFF_MAX_SECONDS = 1800;
    private static final int BATCH_SIZE = 50;
    /** 处理租约时长：内含远程调用（均有超时），租约到期视为恢复器已崩溃，可被接管 */
    private static final long LEASE_SECONDS = 120;

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

    /** 管理员立即重试单笔（B21）：沿用同一 operationId，跳过自动重试上限强制执行，幂等 */
    public void retrySingle(PetOperation operation) {
        try {
            recoverOne(operation, true);
        } catch (Exception e) {
            log.error("管理员重试失败, operationId={}", operation.getOperationId(), e);
            scheduleRetry(operation, "管理员重试失败: " + e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 30000)
    public void recoverPendingOperations() {
        List<PetOperation> operations = operationStore.listRecoverable(BATCH_SIZE);
        for (PetOperation operation : operations) {
            try {
                recoverOne(operation, false);
            } catch (Exception e) {
                log.error("操作恢复失败（下一轮退避重试）, operationId={}, bizType={}",
                        operation.getOperationId(), operation.getBizType(), e);
                scheduleRetry(operation, null);
            }
        }
    }

    /** 包私有供测试：按状态分支收敛单笔操作 */
    void recoverOne(PetOperation operation, boolean force) {
        // 退款分支（TX-04）：COMPENSATING 只允许继续退款收敛，不查原单结果、不重入发放/扣款
        if ("COMPENSATING".equals(operation.getStatus())) {
            resumeRefund(operation);
            return;
        }
        if (!force && !"PROCESSING".equals(operation.getStatus())) {
            // 非强制路径先抢租约（管理员强制重试绕过租约，远程幂等兜底并发双打）
            if (!operationStore.acquireLease(operation, leaseUntil())) {
                return;
            }
        }
        PetWalletOperationVO walletResult = operationService.queryWallet(operation.getOperationId());
        if (walletResult != null && "COMPLETED".equals(walletResult.status())) {
            settleWithWalletResult(operation, walletResult);
            return;
        }
        if (!force && operation.getRetryCount() != null && operation.getRetryCount() >= MAX_AUTO_RETRY) {
            // 保持 UNKNOWN + 长退避：状态可查询，管理员可经 B21 接口按原单强制重试
            scheduleRetry(operation, null);
            log.warn("操作超过自动重试上限，等待人工处理, operationId={}, retryCount={}",
                    operation.getOperationId(), operation.getRetryCount());
            return;
        }
        retryRemote(operation);
    }

    /** 钱包已有结果：按方向与本地事实收敛 */
    private void settleWithWalletResult(PetOperation operation, PetWalletOperationVO walletResult) {
        if ("EARN".equals(operation.getDirection())) {
            settleEarnWithWalletResult(operation, walletResult);
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

    /**
     * EARN 按钱包事实收敛（TX-03）：远程发放已生效时，只有原始状态 UNKNOWN
     * （本地奖励按"结算中"契约已随事务提交）才可直接标完成；PENDING/租约接管行
     * 无法证明本地业务事实仍成立，转 MANUAL_REVIEW 人工核查，禁止猜测补发。
     */
    private void settleEarnWithWalletResult(PetOperation operation, PetWalletOperationVO walletResult) {
        if ("UNKNOWN".equals(operation.getRecoverFromStatus()) || "UNKNOWN".equals(operation.getStatus())) {
            operationStore.markRetry(operation, "COMPLETED", PetJsonUtils.toJson(walletResult), null, null);
            log.info("发放操作按原单结算完成, operationId={}, credited={}",
                    operation.getOperationId(), walletResult.creditedAmount());
            return;
        }
        String reason = "远程发放已生效(credited=" + walletResult.creditedAmount()
                + ")，但本地业务事务结果无法自动判定，需人工核对奖励事实";
        operationStore.markRetry(operation, "MANUAL_REVIEW", PetJsonUtils.toJson(walletResult), reason, null);
        log.warn("发放操作转人工核查, operationId={}", operation.getOperationId());
    }

    /** 按原单号幂等重试远程调用（钱包端去重保证最多生效一次） */
    private void retryRemote(PetOperation operation) {
        try {
            PetWalletOperationVO result = ("SPEND".equals(operation.getDirection())
                    ? wishFeignClient.spendStarlightIdempotent(operation.getUserId(), operation.getAmount(),
                            operation.getBizRefId(), operation.getOperationId()).data()
                    : wishFeignClient.earnStarlightIdempotent(operation.getUserId(), operation.getAmount(),
                            operation.getBizRefId(), operation.getOperationId()).data());
            if ("SPEND".equals(operation.getDirection())) {
                // B01：SPEND 重试成功后同样要补投递本地效果（扣款不入包是严重不一致）
                com.cloudmart.pet.service.PetOperationRecoverable recoverable =
                        recoverablesByBizType.get(operation.getBizType());
                boolean applied = recoverable != null && recoverable.completePendingOperation(operation);
                if (applied) {
                    operationStore.markRetry(operation, "COMPLETED", PetJsonUtils.toJson(result), null, null);
                } else {
                    compensate(operation);
                }
                return;
            }
            // EARN 重试成功：仅本地事实已提交（原始 UNKNOWN）可直接完成；PENDING 转人工
            if ("UNKNOWN".equals(operation.getRecoverFromStatus()) || "UNKNOWN".equals(operation.getStatus())) {
                operationStore.markRetry(operation, "COMPLETED", PetJsonUtils.toJson(result), null, null);
            } else {
                String reason = "恢复重试远程发放已生效，但本地业务事务结果无法自动判定，需人工核对奖励事实";
                operationStore.markRetry(operation, "MANUAL_REVIEW", PetJsonUtils.toJson(result), reason, null);
                log.warn("恢复重试发放转人工核查, operationId={}", operation.getOperationId());
            }
        } catch (com.cloudmart.common.exception.BusinessException e) {
            if (PetOperationService.isDefiniteRejection(e)) {
                // 明确失败（余额不足/内容冲突）：终态 FAILED，不再重试（TX-02）
                operationStore.markRetry(operation, "FAILED", null, e.getMessage(), null);
            } else {
                // 远程不可用：结果未知，退避重试，不当业务失败（TX-02）
                scheduleRetry(operation, "远程不可用: " + e.getCode() + ": " + e.getMessage());
            }
        } catch (Exception e) {
            // TX-02 补强：Feign 402/409 属明确拒绝 → FAILED（远程真机实测 402 曾被误判 UNKNOWN）
            com.cloudmart.common.exception.BusinessException definite = PetOperationService.definiteFromFeign(e);
            if (definite != null) {
                operationStore.markRetry(operation, "FAILED", null, definite.getCode() + ": " + definite.getMessage(), null);
            } else {
                scheduleRetry(operation, e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * 补偿退款（P02/TX-04）：专用退款端点按原单全额原路退回（不走奖励上限截断），
     * 操作键 = 原单摘要派生（固定长度），退款幂等且可关联原单。
     */
    private void compensate(PetOperation operation) {
        // CAS 到 COMPENSATING：0 行说明状态已被他人推进（如并发退款已开始），退出
        if (!operationStore.casStatus(operation, operation.getStatus(), "COMPENSATING")) {
            log.info("补偿退款 CAS 未命中（状态已被他人推进），跳过, operationId={}",
                    operation.getOperationId());
            return;
        }
        executeRefund(operation, "本地无法履约，已按原单退款");
    }

    /** 退款分支续跑（COMPENSATING 扫描恢复/未知重试）：只退款，不进入发放/扣款分支 */
    private void resumeRefund(PetOperation operation) {
        executeRefund(operation, "本地无法履约，已按原单退款");
    }

    private void executeRefund(PetOperation operation, String successNote) {
        String refundOperationId = operationService.refundOperationId(operation.getOperationId());
        try {
            PetWalletOperationVO result = wishFeignClient.refundStarlightIdempotent(operation.getUserId(),
                    operation.getAmount(), operation.getOperationId(), refundOperationId).data();
            operationStore.markRetry(operation, "COMPENSATED", PetJsonUtils.toJson(result),
                    successNote, null);
            log.info("补偿退款完成, operationId={}, refundOperationId={}, credited={}",
                    operation.getOperationId(), refundOperationId,
                    result == null ? null : result.creditedAmount());
        } catch (com.cloudmart.common.exception.BusinessException e) {
            if (PetOperationService.isDefiniteRejection(e)) {
                boolean alreadyRefunded = refundAlreadyCommitted(refundOperationId);
                if (alreadyRefunded) {
                    operationStore.markRetry(operation, "COMPENSATED", null,
                            "退款已提交（冲突命中既有退款单）", null);
                } else {
                    // 明确拒绝（原单越权/无实扣/超退）：转人工，禁止反复重试（TX-04）
                    operationStore.markRetry(operation, "MANUAL_REVIEW", null,
                            "退款被明确拒绝: " + e.getMessage(), null);
                    log.warn("补偿退款被明确拒绝，转人工核查, operationId={}, error={}",
                            operation.getOperationId(), e.getMessage());
                }
            } else {
                // 远程不可用：保持退款分支退避重试
                scheduleRefundRetry(operation, "退款远程不可用: " + e.getMessage());
            }
        } catch (Exception e) {
            // 退款结果未知：保持 COMPENSATING（退款分支），绝不回到通用 UNKNOWN 重入原操作
            scheduleRefundRetry(operation, "退款结果未知: " + e.getMessage());
        }
    }

    /** 冲突拒绝时核对退款单是否已存在（幂等收敛依据） */
    private boolean refundAlreadyCommitted(String refundOperationId) {
        PetWalletOperationVO refund = operationService.queryWallet(refundOperationId);
        return refund != null && "COMPLETED".equals(refund.status());
    }

    /** 退款分支专用退避：状态保持 COMPENSATING，退避时间驱动下一轮续跑 */
    private void scheduleRefundRetry(PetOperation operation, String error) {
        int nextCount = (operation.getRetryCount() == null ? 0 : operation.getRetryCount()) + 1;
        operationStore.markRetry(operation, "COMPENSATING", null, error, backoffAt(nextCount));
    }

    private void scheduleRetry(PetOperation operation, String error) {
        int nextCount = (operation.getRetryCount() == null ? 0 : operation.getRetryCount()) + 1;
        operationStore.markRetry(operation, "UNKNOWN", null, error, backoffAt(nextCount));
    }

    private LocalDateTime leaseUntil() {
        return LocalDateTime.now(ZoneOffset.UTC).plusSeconds(LEASE_SECONDS);
    }

    private LocalDateTime backoffAt(Integer retryCount) {
        int count = retryCount == null ? 1 : retryCount;
        long backoff = Math.min(BACKOFF_BASE_SECONDS * (1L << Math.min(count, 5)), BACKOFF_MAX_SECONDS);
        return LocalDateTime.now(ZoneOffset.UTC).plusSeconds(backoff);
    }
}
