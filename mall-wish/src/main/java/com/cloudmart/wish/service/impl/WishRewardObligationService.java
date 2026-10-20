package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wish.entity.WishRewardObligation;
import com.cloudmart.wish.feign.CommunityFeignClient;
import com.cloudmart.wish.repository.WishRewardObligationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 跨服务奖励交付义务服务（R07）：签到经验→mall-community。
 *
 * <p>交付语义：义务行与源事实同事务提交（uk biz_key 幂等）；事务提交后即送
 * （快路径，成功置 SETTLED）；失败退避补发（30s 起指数退避，封顶 10 分钟）。
 * community 不可用不永久漏发；重复交付由 community 侧 (user,source,bizId) 去重兜底。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WishRewardObligationService {

    /** 退避基数（秒）与最大尝试次数——超过后仍留 PENDING 供后台核查（不自动转 REJECTED） */
    static final int RETRY_BASE_SECONDS = 30;
    static final int MAX_ATTEMPTS = 10;

    private final WishRewardObligationMapper obligationMapper;
    private final CommunityFeignClient communityFeignClient;
    /** T20：统一 UTC 时钟，避免 JVM 默认时区造成时间漂移 */
    private final Clock clock;

    /**
     * 义务入队（调用方事务内）+ 提交后即送。重复 bizKey 幂等返回既有行，
     * 不重复入队不重复即送（补发扫描按 PENDING 收敛）。
     */
    public void enqueueWithCommitDelivery(String bizKey, String source, String sourceBizId,
                                          Long beneficiary, String rewardType, int amount, String description) {
        WishRewardObligation obligation = new WishRewardObligation();
        obligation.setBizKey(bizKey);
        obligation.setSource(source);
        obligation.setSourceBizId(sourceBizId);
        obligation.setBeneficiary(beneficiary);
        obligation.setRewardType(rewardType);
        obligation.setAmount(amount);
        obligation.setDescription(description);
        obligation.setStatus(WishRewardObligation.STATUS_PENDING);
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

    /** 补发扫描：到期 PENDING 逐行尝试（多实例并发由行级条件更新收敛） */
    @Scheduled(fixedDelayString = "${wish.reward-obligation.sweep-delay-ms:15000}")
    public int sweepPending() {
        List<WishRewardObligation> due = obligationMapper.selectList(
                new LambdaQueryWrapper<WishRewardObligation>()
                        .eq(WishRewardObligation::getStatus, WishRewardObligation.STATUS_PENDING)
                        .and(w -> w.isNull(WishRewardObligation::getNextRetryAt)
                                .or().le(WishRewardObligation::getNextRetryAt, LocalDateTime.now(clock)))
                        .orderByAsc(WishRewardObligation::getId)
                        .last("LIMIT 50"));
        int settled = 0;
        for (WishRewardObligation obligation : due) {
            if (deliverQuietly(obligation.getId())) {
                settled++;
            }
        }
        return settled;
    }

    /** 单行交付：成功 SETTLED；失败退避记账；返回是否 SETTLED（异常不外抛） */
    private boolean deliverQuietly(Long obligationId) {
        WishRewardObligation obligation = obligationMapper.selectById(obligationId);
        if (obligation == null || !WishRewardObligation.STATUS_PENDING.equals(obligation.getStatus())) {
            return WishRewardObligation.STATUS_SETTLED.equals(
                    obligation == null ? null : obligation.getStatus());
        }
        try {
            Map<String, Object> body = Map.of(
                    "userId", obligation.getBeneficiary(),
                    "exp", obligation.getAmount(),
                    "sourceBizId", obligation.getBizKey(),
                    "description", obligation.getDescription() == null ? "" : obligation.getDescription());
            ApiResponse<Map<String, Object>> res = communityFeignClient.grantExp(body);
            if (res != null && res.success()) {
                settle(obligation.getId());
                log.info("R07 奖励义务交付完成: bizKey={}, beneficiary={}, amount={}",
                        obligation.getBizKey(), obligation.getBeneficiary(), obligation.getAmount());
                return true;
            }
            recordFailure(obligation, res == null ? "community 空响应" : String.valueOf(res.error()));
            return false;
        } catch (Exception e) {
            recordFailure(obligation, e.getMessage());
            return false;
        }
    }

    private void settle(Long id) {
        obligationMapper.update(null, new LambdaUpdateWrapper<WishRewardObligation>()
                .eq(WishRewardObligation::getId, id)
                .eq(WishRewardObligation::getStatus, WishRewardObligation.STATUS_PENDING)
                .set(WishRewardObligation::getStatus, WishRewardObligation.STATUS_SETTLED)
                .set(WishRewardObligation::getSettledAt, LocalDateTime.now(clock)));
    }

    private void recordFailure(WishRewardObligation obligation, String error) {
        int attempts = (obligation.getAttempts() == null ? 0 : obligation.getAttempts()) + 1;
        long backoff = Math.min((long) RETRY_BASE_SECONDS * (1L << Math.min(attempts - 1, 5)), 600);
        obligationMapper.update(null, new LambdaUpdateWrapper<WishRewardObligation>()
                .eq(WishRewardObligation::getId, obligation.getId())
                .eq(WishRewardObligation::getStatus, WishRewardObligation.STATUS_PENDING)
                .set(WishRewardObligation::getAttempts, attempts)
                .set(WishRewardObligation::getLastError, truncate(error))
                .set(WishRewardObligation::getNextRetryAt, LocalDateTime.now(clock).plusSeconds(backoff)));
        log.warn("R07 奖励义务交付失败（退避 {}s）: bizKey={}, attempts={}, err={}",
                backoff, obligation.getBizKey(), attempts, error);
    }

    private String truncate(String error) {
        if (error == null) {
            return "unknown";
        }
        return error.length() > 300 ? error.substring(0, 300) : error;
    }
}
