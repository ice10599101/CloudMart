package com.cloudmart.user.service.impl;

import com.cloudmart.user.entity.UserInviteQuota;
import com.cloudmart.user.repository.UserInviteQuotaMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T15：额度原子预占——先锁两行、统一判断、统一自增；任一周期满则不占用另一周期。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InviteQuotaServiceTest {

    @Mock
    private UserInviteQuotaMapper quotaMapper;

    private InviteQuotaService service() {
        return new InviteQuotaService(quotaMapper);
    }

    private UserInviteQuota row(int used) {
        UserInviteQuota q = new UserInviteQuota();
        q.setUsedCount(used);
        return q;
    }

    @Test
    @DisplayName("额度内 → 预占成功，日/月各自增一次")
    void reserve_withinLimit_incrementsBoth() {
        when(quotaMapper.selectForUpdate(eq(1L), eq(UserInviteQuota.TYPE_MONTHLY), anyString()))
                .thenReturn(row(3));
        when(quotaMapper.selectForUpdate(eq(1L), eq(UserInviteQuota.TYPE_DAILY), anyString()))
                .thenReturn(row(1));

        var outcome = service().reserve(1L, "2026-10-18", "2026-10", 5, 20);

        assertThat(outcome.reserved()).isTrue();
        verify(quotaMapper).incrementUsed(1L, UserInviteQuota.TYPE_MONTHLY, "2026-10");
        verify(quotaMapper).incrementUsed(1L, UserInviteQuota.TYPE_DAILY, "2026-10-18");
    }

    @Test
    @DisplayName("日额度满 → 拒绝，且月/日都不自增（无半占）")
    void reserve_dailyFull_rejectsWithoutIncrement() {
        when(quotaMapper.selectForUpdate(eq(1L), eq(UserInviteQuota.TYPE_MONTHLY), anyString()))
                .thenReturn(row(3));
        when(quotaMapper.selectForUpdate(eq(1L), eq(UserInviteQuota.TYPE_DAILY), anyString()))
                .thenReturn(row(5));

        var outcome = service().reserve(1L, "2026-10-18", "2026-10", 5, 20);

        assertThat(outcome.reserved()).isFalse();
        assertThat(outcome.dailyUsed()).isEqualTo(5);
        verify(quotaMapper, never()).incrementUsed(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("月额度满 → 拒绝，且月/日都不自增")
    void reserve_monthlyFull_rejectsWithoutIncrement() {
        when(quotaMapper.selectForUpdate(eq(1L), eq(UserInviteQuota.TYPE_MONTHLY), anyString()))
                .thenReturn(row(20));
        when(quotaMapper.selectForUpdate(eq(1L), eq(UserInviteQuota.TYPE_DAILY), anyString()))
                .thenReturn(row(1));

        var outcome = service().reserve(1L, "2026-10-18", "2026-10", 5, 20);

        assertThat(outcome.reserved()).isFalse();
        assertThat(outcome.monthlyUsed()).isEqualTo(20);
        verify(quotaMapper, never()).incrementUsed(anyLong(), anyString(), anyString());
    }
}
