package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wish.entity.WishInviteCallbackOutbox;
import com.cloudmart.wish.feign.UserFeignClient;
import com.cloudmart.wish.repository.WishInviteCallbackOutboxMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * T15：邀请资格回调 outbox（持久重试）。
 *
 * <p>此前 WISH-04 资格回调在签到事务 afterCommit 直接 Feign 即发即弃，失败靠
 * "下次签到自然重试"——用户停签即永久丢失。本服务把回调落库为 outbox 行：</p>
 * <ul>
 *   <li>签到事务内 enqueue（同用户旧 PENDING 置 SUPERSEDED——快照语义只保留最新）；</li>
 *   <li>提交后即送（afterCommit）；失败退避补发（@Scheduled 扫描，多实例并发由
 *       行级条件更新收敛，与 R07 奖励义务同模式）；</li>
 *   <li>超过 {@link #MAX_ATTEMPTS} 次置 FAILED 留痕（不无限堆积）。</li>
 * </ul>
 */
@Slf4j
@Service
@EnableScheduling
@RequiredArgsConstructor
public class WishInviteCallbackOutboxService {

    /** 退避基数（秒）：4/8/16/32/64/128/256/512 封顶 600 */
    private static final int RETRY_BASE_SECONDS = 4;

    private final WishInviteCallbackOutboxMapper outboxMapper;
    private final UserFeignClient userFeignClient;

    /**
     * 签到事务内调用：同用户旧 PENDING 置 SUPERSEDED 后插入新快照行；
     * 提交后立即尝试一次（降低正常路径延迟），失败交给 sweep。
     */
    @Transactional(rollbackFor = Exception.class)
    public void enqueue(Long userId, int distinctSigninDays) {
        // 快照语义：只保留同用户最新一条 PENDING
        outboxMapper.update(null, new LambdaUpdateWrapper<WishInviteCallbackOutbox>()
                .eq(WishInviteCallbackOutbox::getUserId, userId)
                .eq(WishInviteCallbackOutbox::getStatus, WishInviteCallbackOutbox.STATUS_PENDING)
                .set(WishInviteCallbackOutbox::getStatus, WishInviteCallbackOutbox.STATUS_SUPERSEDED));

        WishInviteCallbackOutbox row = new WishInviteCallbackOutbox();
        row.setUserId(userId);
        row.setDistinctSigninDays(distinctSigninDays);
        row.setStatus(WishInviteCallbackOutbox.STATUS_PENDING);
        row.setAttempts(0);
        outboxMapper.insert(row);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deliverQuietly(row.getId());
                }
            });
        } else {
            deliverQuietly(row.getId());
        }
    }

    /** 补发扫描：到期 PENDING 逐行尝试（行级条件更新收敛并发） */
    @Scheduled(fixedDelayString = "${wish.invite-callback.sweep-delay-ms:30000}")
    public int sweepPending() {
        List<WishInviteCallbackOutbox> due = outboxMapper.selectList(
                new LambdaQueryWrapper<WishInviteCallbackOutbox>()
                        .eq(WishInviteCallbackOutbox::getStatus, WishInviteCallbackOutbox.STATUS_PENDING)
                        .and(w -> w.isNull(WishInviteCallbackOutbox::getNextRetryAt)
                                .or().le(WishInviteCallbackOutbox::getNextRetryAt, LocalDateTime.now()))
                        .orderByAsc(WishInviteCallbackOutbox::getId)
                        .last("LIMIT 50"));
        int settled = 0;
        for (WishInviteCallbackOutbox row : due) {
            if (deliverQuietly(row.getId())) {
                settled++;
            }
        }
        return settled;
    }

    /** 单行交付：成功 SETTLED；失败退避；到顶 FAILED 留痕（异常不外抛） */
    private boolean deliverQuietly(Long rowId) {
        WishInviteCallbackOutbox row = outboxMapper.selectById(rowId);
        if (row == null || !WishInviteCallbackOutbox.STATUS_PENDING.equals(row.getStatus())) {
            return WishInviteCallbackOutbox.STATUS_SETTLED.equals(
                    row == null ? null : row.getStatus());
        }
        try {
            ApiResponse<Map<String, Object>> res = userFeignClient.inviteEligibilityCheck(
                    Map.of("userId", row.getUserId(), "distinctSigninDays", row.getDistinctSigninDays()));
            if (res != null && res.success()) {
                settle(row.getId());
                log.info("[T15] 邀请资格回调交付完成: userId={}, distinctDays={}",
                        row.getUserId(), row.getDistinctSigninDays());
                return true;
            }
            recordFailure(row, res == null ? "user 空响应" : String.valueOf(res.error()));
            return false;
        } catch (Exception e) {
            recordFailure(row, e.getMessage());
            return false;
        }
    }

    private void settle(Long id) {
        outboxMapper.update(null, new LambdaUpdateWrapper<WishInviteCallbackOutbox>()
                .eq(WishInviteCallbackOutbox::getId, id)
                .eq(WishInviteCallbackOutbox::getStatus, WishInviteCallbackOutbox.STATUS_PENDING)
                .set(WishInviteCallbackOutbox::getStatus, WishInviteCallbackOutbox.STATUS_SETTLED)
                .set(WishInviteCallbackOutbox::getSettledAt, LocalDateTime.now()));
    }

    private void recordFailure(WishInviteCallbackOutbox row, String error) {
        int attempts = (row.getAttempts() == null ? 0 : row.getAttempts()) + 1;
        if (attempts >= WishInviteCallbackOutbox.MAX_ATTEMPTS) {
            outboxMapper.update(null, new LambdaUpdateWrapper<WishInviteCallbackOutbox>()
                    .eq(WishInviteCallbackOutbox::getId, row.getId())
                    .eq(WishInviteCallbackOutbox::getStatus, WishInviteCallbackOutbox.STATUS_PENDING)
                    .set(WishInviteCallbackOutbox::getStatus, WishInviteCallbackOutbox.STATUS_FAILED)
                    .set(WishInviteCallbackOutbox::getAttempts, attempts)
                    .set(WishInviteCallbackOutbox::getLastError, truncate(error)));
            log.error("[T15] 邀请资格回调补发到顶置 FAILED（需人工介入）: userId={}, attempts={}, err={}",
                    row.getUserId(), attempts, error);
            return;
        }
        long backoff = Math.min((long) RETRY_BASE_SECONDS * (1L << Math.min(attempts - 1, 7)), 600);
        outboxMapper.update(null, new LambdaUpdateWrapper<WishInviteCallbackOutbox>()
                .eq(WishInviteCallbackOutbox::getId, row.getId())
                .eq(WishInviteCallbackOutbox::getStatus, WishInviteCallbackOutbox.STATUS_PENDING)
                .set(WishInviteCallbackOutbox::getAttempts, attempts)
                .set(WishInviteCallbackOutbox::getLastError, truncate(error))
                .set(WishInviteCallbackOutbox::getNextRetryAt, LocalDateTime.now().plusSeconds(backoff)));
        log.warn("[T15] 邀请资格回调交付失败（退避 {}s）: userId={}, attempts={}, err={}",
                backoff, row.getUserId(), attempts, error);
    }

    private String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() > 480 ? error.substring(0, 480) : error;
    }
}
