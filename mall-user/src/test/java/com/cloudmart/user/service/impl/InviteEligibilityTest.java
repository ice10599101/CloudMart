package com.cloudmart.user.service.impl;

import com.cloudmart.user.config.UserInviteProperties;
import com.cloudmart.user.entity.User;
import com.cloudmart.user.entity.UserInviteRelation;
import com.cloudmart.user.repository.UserInviteRelationMapper;
import com.cloudmart.user.repository.UserMapper;
import com.cloudmart.user.service.impl.UserRewardObligationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WISH-04 验收：资格门槛（2 个不同业务日签到 + 账号 24h）、额度超限拒绝、
 * 受限账号 HOLD、达到资格仅发一次（GRANTED 幂等）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InviteEligibilityTest {

    private static final long INVITER = 1L;
    private static final long INVITEE = 2L;

    @Mock
    private com.cloudmart.user.repository.UserInviteCodeMapper inviteCodeMapper;
    @Mock
    private UserInviteRelationMapper inviteRelationMapper;
    @Mock
    private UserRewardObligationService rewardObligationService;
    @Mock
    private UserMapper userMapper;

    private InviteServiceImpl service;
    private UserInviteProperties properties;

    private UserInviteRelation relation(String status) {
        UserInviteRelation r = new UserInviteRelation();
        r.setId(10L);
        r.setInviterId(INVITER);
        r.setInviteeId(INVITEE);
        r.setRewardStatus(status);
        r.setRuleVersion(1);
        return r;
    }

    private User user(int ageHours, int status) {
        User u = new User();
        u.setId(INVITEE);
        u.setCreatedAt(LocalDateTime.now(java.time.ZoneOffset.UTC).minusHours(ageHours));
        u.setStatus(status);
        return u;
    }

    @BeforeEach
    void setUp() {
        properties = new UserInviteProperties();
        service = new InviteServiceImpl(inviteCodeMapper, inviteRelationMapper,
                rewardObligationService, userMapper, properties);
    }

    @Test
    @DisplayName("签到不足 2 个不同业务日 → 保持 PENDING_ELIGIBILITY，不发奖")
    void signinNotEnough_pending() {
        when(inviteRelationMapper.selectOne(any())).thenReturn(relation("PENDING_ELIGIBILITY"));
        when(userMapper.selectById(INVITEE)).thenReturn(user(72, 1));

        Map<String, Object> result = service.eligibilityCheck(INVITEE, 1);

        assertThat(result).containsEntry("result", "pending");
        verify(rewardObligationService, never()).enqueueWithCommitDelivery(
                anyString(), anyString(), anyString(), anyLong(), anyString(), anyInt(), anyString());
    }

    @Test
    @DisplayName("账号未满 24h → pending（防批量注册即时薅）")
    void accountTooYoung_pending() {
        when(inviteRelationMapper.selectOne(any())).thenReturn(relation("PENDING_ELIGIBILITY"));
        when(userMapper.selectById(INVITEE)).thenReturn(user(2, 1));

        Map<String, Object> result = service.eligibilityCheck(INVITEE, 5);

        assertThat(result).containsEntry("result", "pending");
        verify(rewardObligationService, never()).enqueueWithCommitDelivery(
                anyString(), anyString(), anyString(), anyLong(), anyString(), anyInt(), anyString());
    }

    @Test
    @DisplayName("达标 + 额度内 → GRANTED，双方义务各一次")
    void eligible_grantedOnce() {
        when(inviteRelationMapper.selectOne(any())).thenReturn(relation("PENDING_ELIGIBILITY"));
        when(userMapper.selectById(INVITEE)).thenReturn(user(72, 1));
        when(inviteRelationMapper.selectList(any())).thenReturn(List.of()); // 无已发放
        when(inviteRelationMapper.updateById(any(UserInviteRelation.class))).thenReturn(1);

        Map<String, Object> result = service.eligibilityCheck(INVITEE, 2);

        assertThat(result).containsEntry("result", "granted");
        verify(rewardObligationService, org.mockito.Mockito.times(2)).enqueueWithCommitDelivery(
                anyString(), anyString(), anyString(), anyLong(), anyString(), anyInt(), anyString());
        ArgumentCaptor<UserInviteRelation> captor = ArgumentCaptor.forClass(UserInviteRelation.class);
        verify(inviteRelationMapper, org.mockito.Mockito.atLeastOnce()).updateById(captor.capture());
        assertThat(captor.getAllValues().stream()
                .map(UserInviteRelation::getRewardStatus)
                .reduce((a, b) -> b)).contains("GRANTED");
    }

    @Test
    @DisplayName("每日额度满 → REJECTED_LIMIT（原因台账可查），不发奖")
    void quotaExceeded_rejected() {
        when(inviteRelationMapper.selectOne(any())).thenReturn(relation("PENDING_ELIGIBILITY"));
        when(userMapper.selectById(INVITEE)).thenReturn(user(72, 1));
        UserInviteRelation used = relation("GRANTED");
        used.setEligibleAt(LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(5));
        when(inviteRelationMapper.selectList(any())).thenReturn(List.of(
                used, used, used, used, used)); // 已发 5 个=每日上限

        Map<String, Object> result = service.eligibilityCheck(INVITEE, 5);

        assertThat(result).containsEntry("result", "rejected");
        verify(rewardObligationService, never()).enqueueWithCommitDelivery(
                anyString(), anyString(), anyString(), anyLong(), anyString(), anyInt(), anyString());
    }

    @Test
    @DisplayName("受限账号 → HOLD 待审核")
    void restricted_hold() {
        when(inviteRelationMapper.selectOne(any())).thenReturn(relation("PENDING_ELIGIBILITY"));
        when(userMapper.selectById(INVITEE)).thenReturn(user(72, 0));

        Map<String, Object> result = service.eligibilityCheck(INVITEE, 5);

        assertThat(result).containsEntry("result", "hold");
        verify(rewardObligationService, never()).enqueueWithCommitDelivery(
                anyString(), anyString(), anyString(), anyLong(), anyString(), anyInt(), anyString());
    }

    @Test
    @DisplayName("已达 GRANTED → 重复回调跳过（达到资格仅发一次）")
    void alreadyGranted_skip() {
        when(inviteRelationMapper.selectOne(any())).thenReturn(relation("GRANTED"));

        Map<String, Object> result = service.eligibilityCheck(INVITEE, 5);

        assertThat(result).containsEntry("result", "skipped");
        verify(rewardObligationService, never()).enqueueWithCommitDelivery(
                anyString(), anyString(), anyString(), anyLong(), anyString(), anyInt(), anyString());
    }
}
