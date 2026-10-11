package com.cloudmart.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.user.entity.UserRewardObligation;
import com.cloudmart.user.feign.WishStarlightFeignClient;
import com.cloudmart.user.repository.UserRewardObligationMapper;
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
 * 邀请奖励交付义务服务（R07）：mall-user → mall-wish 星光。
 *
 * <p>义务行与绑定关系同事务提交（uk biz_key——每个邀请关系每个受益人至多一次）；
 * 事务提交后即送（快路径），失败退避补发——修复"绑定事务回滚留下已发远端奖励/
 * 远端失败永久漏发"。重复交付由 wish 侧 operationId 幂等兜底。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserRewardObligationService {

    static final int RETRY_BASE_SECONDS = 30;

    private final UserRewardObligationMapper obligationMapper;
    private final WishStarlightFeignClient wishStarlightFeignClient;

    /** 义务入队（调用方事务内）+ 提交后即送；重复 bizKey 幂等跳过 */
    public void enqueueWithCommitDelivery(String bizKey, String source, String sourceBizId,
                                          Long beneficiary, String rewardType, int amount, String description) {
        UserRewardObligation obligation = new UserRewardObligation();
        obligation.setBizKey(bizKey);
        obligation.setSource(source);
        obligation.setSourceBizId(sourceBizId);
        obligation.setBeneficiary(beneficiary);
        obligation.setRewardType(rewardType);
        obligation.setAmount(amount);
        obligation.setDescription(description);
        obligation.setStatus(UserRewardObligation.STATUS_PENDING);
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
    @Scheduled(fixedDelayString = "${user.reward-obligation.sweep-delay-ms:15000}")
    public int sweepPending() {
        List<UserRewardObligation> due = obligationMapper.selectList(
                new LambdaQueryWrapper<UserRewardObligation>()
                        .eq(UserRewardObligation::getStatus, UserRewardObligation.STATUS_PENDING)
                        .and(w -> w.isNull(UserRewardObligation::getNextRetryAt)
                                .or().le(UserRewardObligation::getNextRetryAt, LocalDateTime.now()))
                        .orderByAsc(UserRewardObligation::getId)
                        .last("LIMIT 50"));
        int settled = 0;
        for (UserRewardObligation obligation : due) {
            if (deliverQuietly(obligation.getId())) {
                settled++;
            }
        }
        return settled;
    }

    private boolean deliverQuietly(Long obligationId) {
        UserRewardObligation obligation = obligationMapper.selectById(obligationId);
        if (obligation == null || !UserRewardObligation.STATUS_PENDING.equals(obligation.getStatus())) {
            return UserRewardObligation.STATUS_SETTLED.equals(
                    obligation == null ? null : obligation.getStatus());
        }
        try {
            ApiResponse<Map<String, Object>> res = wishStarlightFeignClient.earn(
                    obligation.getBeneficiary(), obligation.getAmount(),
                    Long.valueOf(obligation.getSourceBizId()), obligation.getBizKey(),
                    obligation.getSource());
            if (res != null && res.success()) {
                settle(obligation.getId());
                log.info("R07 邀请奖励义务交付完成: bizKey={}, beneficiary={}, amount={}",
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
        obligationMapper.update(null, new LambdaUpdateWrapper<UserRewardObligation>()
                .eq(UserRewardObligation::getId, id)
                .eq(UserRewardObligation::getStatus, UserRewardObligation.STATUS_PENDING)
                .set(UserRewardObligation::getStatus, UserRewardObligation.STATUS_SETTLED)
                .set(UserRewardObligation::getSettledAt, LocalDateTime.now()));
    }

    private void recordFailure(UserRewardObligation obligation, String error) {
        int attempts = (obligation.getAttempts() == null ? 0 : obligation.getAttempts()) + 1;
        long backoff = Math.min((long) RETRY_BASE_SECONDS * (1L << Math.min(attempts - 1, 5)), 600);
        obligationMapper.update(null, new LambdaUpdateWrapper<UserRewardObligation>()
                .eq(UserRewardObligation::getId, obligation.getId())
                .eq(UserRewardObligation::getStatus, UserRewardObligation.STATUS_PENDING)
                .set(UserRewardObligation::getAttempts, attempts)
                .set(UserRewardObligation::getLastError, truncate(error))
                .set(UserRewardObligation::getNextRetryAt, LocalDateTime.now().plusSeconds(backoff)));
        log.warn("R07 邀请奖励义务交付失败（退避 {}s）: bizKey={}, attempts={}, err={}",
                backoff, obligation.getBizKey(), attempts, error);
    }

    private String truncate(String error) {
        if (error == null) {
            return "unknown";
        }
        return error.length() > 300 ? error.substring(0, 300) : error;
    }
}
