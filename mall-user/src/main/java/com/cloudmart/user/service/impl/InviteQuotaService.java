package com.cloudmart.user.service.impl;

import com.cloudmart.user.entity.UserInviteQuota;
import com.cloudmart.user.repository.UserInviteQuotaMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 邀请奖励周期额度原子预占（T15）。
 *
 * <p>固定加锁顺序（先月后日）避免多周期预占死锁；先对两行 {@code FOR UPDATE}
 * 后统一判断、统一自增——任一周期满则不占用另一周期，杜绝“日已占、月被拒”的半占状态。</p>
 */
@Service
@RequiredArgsConstructor
public class InviteQuotaService {

    private final UserInviteQuotaMapper quotaMapper;

    /** 预占结果：成功，或命中限额时的当前用量/上限（供台账原因展示） */
    public record ReserveOutcome(boolean reserved, int dailyUsed, int dailyLimit,
                                 int monthlyUsed, int monthlyLimit) {
        public static ReserveOutcome ok() {
            return new ReserveOutcome(true, 0, 0, 0, 0);
        }
        public static ReserveOutcome limitReached(int dailyUsed, int dailyLimit,
                                                  int monthlyUsed, int monthlyLimit) {
            return new ReserveOutcome(false, dailyUsed, dailyLimit, monthlyUsed, monthlyLimit);
        }
    }

    /**
     * 在调用方事务内原子预占日/月两个名额。
     *
     * @param inviterId    邀请人
     * @param dayPeriod    业务日标识（Asia/Shanghai，yyyy-MM-dd）
     * @param monthPeriod  业务月标识（yyyy-MM）
     * @param dailyLimit   日上限
     * @param monthlyLimit 月上限
     */
    public ReserveOutcome reserve(Long inviterId, String dayPeriod, String monthPeriod,
                                  int dailyLimit, int monthlyLimit) {
        // 固定顺序：先月后日（与并发调用一致，避免交叉锁死）
        ensureRow(inviterId, UserInviteQuota.TYPE_MONTHLY, monthPeriod);
        UserInviteQuota monthly = quotaMapper.selectForUpdate(inviterId, UserInviteQuota.TYPE_MONTHLY, monthPeriod);
        ensureRow(inviterId, UserInviteQuota.TYPE_DAILY, dayPeriod);
        UserInviteQuota daily = quotaMapper.selectForUpdate(inviterId, UserInviteQuota.TYPE_DAILY, dayPeriod);

        int mUsed = monthly.getUsedCount() == null ? 0 : monthly.getUsedCount();
        int dUsed = daily.getUsedCount() == null ? 0 : daily.getUsedCount();
        if (mUsed >= monthlyLimit || dUsed >= dailyLimit) {
            return ReserveOutcome.limitReached(dUsed, dailyLimit, mUsed, monthlyLimit);
        }
        quotaMapper.incrementUsed(inviterId, UserInviteQuota.TYPE_MONTHLY, monthPeriod);
        quotaMapper.incrementUsed(inviterId, UserInviteQuota.TYPE_DAILY, dayPeriod);
        return ReserveOutcome.ok();
    }

    private void ensureRow(Long inviterId, String quotaType, String period) {
        quotaMapper.insertIgnore(inviterId, quotaType, period);
    }
}
