package com.cloudmart.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.product.entity.ProductRewardObligation;
import com.cloudmart.product.feign.WishStarlightFeignClient;
import com.cloudmart.product.repository.ProductRewardObligationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 评价奖励交付义务服务（R07）：mall-product → mall-wish 星光。
 *
 * <p>义务行与评价行同事务提交（uk biz_key——每个评价至多一次）；事务提交后即送
 * （快路径），失败退避补发——修复"afterCommit 即时调用失败只留日志、进程崩溃后
 * 永久漏发"。重复交付由 wish 侧 operationId 幂等兜底。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductRewardObligationService {

    static final int RETRY_BASE_SECONDS = 30;

    private final ProductRewardObligationMapper obligationMapper;
    private final WishStarlightFeignClient wishStarlightFeignClient;

    /** 义务入队（调用方事务内）+ 提交后即送；重复 bizKey 幂等跳过 */
    public void enqueueWithCommitDelivery(String bizKey, String source, String sourceBizId,
                                          Long beneficiary, String rewardType, int amount, String description) {
        ProductRewardObligation obligation = new ProductRewardObligation();
        obligation.setBizKey(bizKey);
        obligation.setSource(source);
        obligation.setSourceBizId(sourceBizId);
        obligation.setBeneficiary(beneficiary);
        obligation.setRewardType(rewardType);
        obligation.setAmount(amount);
        obligation.setDescription(description);
        obligation.setStatus(ProductRewardObligation.STATUS_PENDING);
        obligation.setAttempts(0);
        try {
            obligationMapper.insert(obligation);
        } catch (DuplicateKeyException alreadyEnqueued) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deliverQuietly(obligation.getId());
                }
            });
        } else {
            deliverQuietly(obligation.getId());
        }
    }

    /** 补发扫描：到期 PENDING 逐行尝试 */
    @Scheduled(fixedDelayString = "${product.reward-obligation.sweep-delay-ms:15000}")
    public int sweepPending() {
        List<ProductRewardObligation> due = obligationMapper.selectList(
                new LambdaQueryWrapper<ProductRewardObligation>()
                        .eq(ProductRewardObligation::getStatus, ProductRewardObligation.STATUS_PENDING)
                        .and(w -> w.isNull(ProductRewardObligation::getNextRetryAt)
                                .or().le(ProductRewardObligation::getNextRetryAt, LocalDateTime.now()))
                        .orderByAsc(ProductRewardObligation::getId)
                        .last("LIMIT 50"));
        int settled = 0;
        for (ProductRewardObligation obligation : due) {
            if (deliverQuietly(obligation.getId())) {
                settled++;
            }
        }
        return settled;
    }

    private boolean deliverQuietly(Long obligationId) {
        ProductRewardObligation obligation = obligationMapper.selectById(obligationId);
        if (obligation == null || !ProductRewardObligation.STATUS_PENDING.equals(obligation.getStatus())) {
            return ProductRewardObligation.STATUS_SETTLED.equals(
                    obligation == null ? null : obligation.getStatus());
        }
        try {
            ApiResponse<Map<String, Object>> res = wishStarlightFeignClient.earn(
                    obligation.getBeneficiary(), obligation.getAmount(),
                    Long.valueOf(obligation.getSourceBizId()), obligation.getBizKey(),
                    obligation.getSource());
            if (res != null && res.success()) {
                settle(obligation.getId());
                log.info("R07 评价奖励义务交付完成: bizKey={}, beneficiary={}, amount={}",
                        obligation.getBizKey(), obligation.getBeneficiary(), obligation.getAmount());
                return true;
            }
            recordFailure(obligation, res == null ? "wish 空响应" : String.valueOf(res.error()));
            return false;
        } catch (Exception e) {
            recordFailure(obligation, e.getMessage());
            return false;
        }
    }

    private void settle(Long id) {
        obligationMapper.update(null, new LambdaUpdateWrapper<ProductRewardObligation>()
                .eq(ProductRewardObligation::getId, id)
                .eq(ProductRewardObligation::getStatus, ProductRewardObligation.STATUS_PENDING)
                .set(ProductRewardObligation::getStatus, ProductRewardObligation.STATUS_SETTLED)
                .set(ProductRewardObligation::getSettledAt, LocalDateTime.now()));
    }

    private void recordFailure(ProductRewardObligation obligation, String error) {
        int attempts = (obligation.getAttempts() == null ? 0 : obligation.getAttempts()) + 1;
        long backoff = Math.min((long) RETRY_BASE_SECONDS * (1L << Math.min(attempts - 1, 5)), 600);
        obligationMapper.update(null, new LambdaUpdateWrapper<ProductRewardObligation>()
                .eq(ProductRewardObligation::getId, obligation.getId())
                .eq(ProductRewardObligation::getStatus, ProductRewardObligation.STATUS_PENDING)
                .set(ProductRewardObligation::getAttempts, attempts)
                .set(ProductRewardObligation::getLastError, truncate(error))
                .set(ProductRewardObligation::getNextRetryAt, LocalDateTime.now().plusSeconds(backoff)));
        log.warn("R07 评价奖励义务交付失败（退避 {}s）: bizKey={}, attempts={}, err={}",
                backoff, obligation.getBizKey(), attempts, error);
    }

    private String truncate(String error) {
        if (error == null) {
            return "unknown";
        }
        return error.length() > 300 ? error.substring(0, 300) : error;
    }
}
