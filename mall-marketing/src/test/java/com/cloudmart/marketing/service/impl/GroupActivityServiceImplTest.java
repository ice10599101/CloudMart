package com.cloudmart.marketing.service.impl;

import com.cloudmart.common.async.outbox.OutboxService;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.marketing.converter.MarketingConverter;
import com.cloudmart.marketing.dto.GroupActivityDTO;
import com.cloudmart.marketing.dto.GroupOrderDTO;
import com.cloudmart.marketing.dto.JoinGroupRequest;
import com.cloudmart.marketing.entity.GroupActivity;
import com.cloudmart.marketing.entity.GroupMember;
import com.cloudmart.marketing.entity.GroupOrder;
import com.cloudmart.marketing.repository.GroupActivityMapper;
import com.cloudmart.marketing.repository.GroupMemberMapper;
import com.cloudmart.marketing.repository.GroupOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * T10 拼团服务测试：活动归属校验、DB 权威人数/开团数原子递增、
 * 成员唯一键裁决、成团 CAS + 同事务 Outbox、过期释放权益。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GroupActivityServiceImplTest {

    @Mock
    private GroupActivityMapper activityMapper;

    @Mock
    private GroupOrderMapper groupOrderMapper;

    @Mock
    private GroupMemberMapper memberMapper;

    @Mock
    private MarketingConverter converter;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private OutboxService outboxService;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private GroupActivityServiceImpl groupActivityService;

    private static final Long USER_ID = 1001L;
    private static final Long ACTIVITY_ID = 2001L;
    private static final Long GROUP_ORDER_ID = 3001L;
    private static final Long PRODUCT_ID = 4001L;
    private static final Long SKU_ID = 5001L;

    private GroupActivity enabledActivity;

    @BeforeEach
    void setUp() {
        groupActivityService = new GroupActivityServiceImpl(
                activityMapper, groupOrderMapper, memberMapper, converter,
                redisTemplate, outboxService, new ObjectMapper());

        enabledActivity = new GroupActivity();
        enabledActivity.setId(ACTIVITY_ID);
        enabledActivity.setName("Test Group");
        enabledActivity.setProductId(PRODUCT_ID);
        enabledActivity.setSkuId(SKU_ID);
        enabledActivity.setOriginalPrice(new BigDecimal("199.00"));
        enabledActivity.setGroupPrice(new BigDecimal("99.00"));
        enabledActivity.setTargetNumber(3);
        enabledActivity.setMaxGroups(10);
        enabledActivity.setCurrentGroups(0);
        enabledActivity.setPerUserLimit(1);
        enabledActivity.setStartTime(LocalDateTime.now().minusHours(1));
        enabledActivity.setEndTime(LocalDateTime.now().plusHours(24));
        enabledActivity.setStatus("ENABLED");

        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(enabledActivity);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        // Redis 预筛默认放行（resultCode=0：未成团）
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(0));
        when(memberMapper.insert(any(GroupMember.class))).thenReturn(1);
        when(groupOrderMapper.incrementMemberCount(GROUP_ORDER_ID)).thenReturn(1);
        when(groupOrderMapper.selectById(GROUP_ORDER_ID)).thenReturn(pendingGroup());
        when(memberMapper.selectList(any())).thenReturn(List.of(member(USER_ID)));
    }

    private GroupOrder pendingGroup() {
        GroupOrder groupOrder = new GroupOrder();
        groupOrder.setId(GROUP_ORDER_ID);
        groupOrder.setActivityId(ACTIVITY_ID);
        groupOrder.setLeaderUserId(USER_ID);
        groupOrder.setCurrentNumber(1);
        groupOrder.setTargetNumber(3);
        groupOrder.setStatus("PENDING");
        groupOrder.setExpireTime(LocalDateTime.now().plusHours(47));
        return groupOrder;
    }

    private GroupMember member(Long userId) {
        GroupMember member = new GroupMember();
        member.setGroupOrderId(GROUP_ORDER_ID);
        member.setUserId(userId);
        member.setActivityId(ACTIVITY_ID);
        member.setStatus("JOINED");
        return member;
    }

    @Nested
    @DisplayName("joinGroup 参团与开团")
    class JoinGroupTests {

        @Test
        @DisplayName("活动不存在 → ACTIVITY_NOT_FOUND")
        void joinGroup_activityNotFound_throwsException() {
            JoinGroupRequest request = new JoinGroupRequest(null, ACTIVITY_ID);
            when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(null);

            assertThatThrownBy(() -> groupActivityService.joinGroup(USER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("ACTIVITY_NOT_FOUND");
        }

        @Test
        @DisplayName("活动未启用/未在进行中 → 拒绝")
        void joinGroup_activityNotAvailable_throwsException() {
            JoinGroupRequest request = new JoinGroupRequest(null, ACTIVITY_ID);
            enabledActivity.setStatus("DISABLED");

            assertThatThrownBy(() -> groupActivityService.joinGroup(USER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("ACTIVITY_NOT_ENABLED");

            enabledActivity.setStatus("ENABLED");
            enabledActivity.setEndTime(LocalDateTime.now().minusHours(1));
            assertThatThrownBy(() -> groupActivityService.joinGroup(USER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("ACTIVITY_NOT_IN_PROGRESS");
        }

        @Test
        @DisplayName("T10：参团组不属于该活动 → GROUP_ACTIVITY_MISMATCH（活动 A 不接受活动 B 的组）")
        void joinGroup_groupActivityMismatch_throwsException() {
            JoinGroupRequest request = new JoinGroupRequest(GROUP_ORDER_ID, ACTIVITY_ID);
            GroupOrder otherActivityResult = pendingGroup();
            otherActivityResult.setActivityId(9999L);
            when(groupOrderMapper.selectById(GROUP_ORDER_ID)).thenReturn(otherActivityResult);

            assertThatThrownBy(() -> groupActivityService.joinGroup(USER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("GROUP_ACTIVITY_MISMATCH");
        }

        @Test
        @DisplayName("T10：参团组已终态（非 PENDING）→ GROUP_NOT_PENDING")
        void joinGroup_groupNotPending_throwsException() {
            JoinGroupRequest request = new JoinGroupRequest(GROUP_ORDER_ID, ACTIVITY_ID);
            GroupOrder settled = pendingGroup();
            settled.setStatus("SUCCESS");
            when(groupOrderMapper.selectById(GROUP_ORDER_ID)).thenReturn(settled);

            assertThatThrownBy(() -> groupActivityService.joinGroup(USER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("GROUP_NOT_PENDING");
        }

        @Test
        @DisplayName("T10：开团数原子递增 0 行 → MAX_GROUPS_REACHED（不再读改写）")
        void openGroup_maxGroupsReached_throwsException() {
            JoinGroupRequest request = new JoinGroupRequest(null, ACTIVITY_ID);
            enabledActivity.setMaxGroups(2);
            when(groupOrderMapper.incrementActivityGroups(ACTIVITY_ID)).thenReturn(0);

            assertThatThrownBy(() -> groupActivityService.joinGroup(USER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("MAX_GROUPS_REACHED");
        }

        @Test
        @DisplayName("Redis 预筛拒绝（-3 已参过活动）→ USER_ALREADY_JOINED_ACTIVITY")
        void joinGroup_luaPrefilterAlreadyJoined_throwsException() {
            JoinGroupRequest request = new JoinGroupRequest(GROUP_ORDER_ID, ACTIVITY_ID);
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(List.of(-3));

            assertThatThrownBy(() -> groupActivityService.joinGroup(USER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("USER_ALREADY_JOINED_ACTIVITY");
        }

        @Test
        @DisplayName("T10：成员唯一键冲突（预筛投影陈旧）→ DB 裁决 USER_ALREADY_JOINED_ACTIVITY")
        void joinGroup_duplicateMemberInsert_dbAuthorityRejects() {
            JoinGroupRequest request = new JoinGroupRequest(GROUP_ORDER_ID, ACTIVITY_ID);
            when(memberMapper.insert(any(GroupMember.class)))
                    .thenThrow(new DuplicateKeyException("uk_group_members_activity_user"));

            assertThatThrownBy(() -> groupActivityService.joinGroup(USER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("USER_ALREADY_JOINED_ACTIVITY");
        }

        @Test
        @DisplayName("T10：DB 人数递增 0 行（满员/已终态）→ GROUP_FULL")
        void joinGroup_memberCountIncrementFails_throwsException() {
            JoinGroupRequest request = new JoinGroupRequest(GROUP_ORDER_ID, ACTIVITY_ID);
            when(groupOrderMapper.incrementMemberCount(GROUP_ORDER_ID)).thenReturn(0);

            assertThatThrownBy(() -> groupActivityService.joinGroup(USER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("GROUP_FULL");
        }

        @Test
        @DisplayName("参团成功（未成团）→ CAS 成团不生效（人数不足），无成团事件")
        void joinGroup_successNoGroupFormation_returnsDto() {
            JoinGroupRequest request = new JoinGroupRequest(GROUP_ORDER_ID, ACTIVITY_ID);
            // 未成团：markSuccess CAS 返回 0（真实 SQL 由 current_number >= target_number 裁决）
            when(groupOrderMapper.markSuccess(eq(GROUP_ORDER_ID), any())).thenReturn(0);

            GroupOrderDTO result = groupActivityService.joinGroup(USER_ID, request);

            assertThat(result).isNotNull();
            verify(memberMapper).insert(any(GroupMember.class));
            verify(groupOrderMapper).incrementMemberCount(GROUP_ORDER_ID);
            verify(groupOrderMapper).markSuccess(eq(GROUP_ORDER_ID), any());
            verify(outboxService, never()).record(any());
        }

        @Test
        @DisplayName("T10：成团 → CAS 落 SUCCESS + 同事务 Outbox 事件（稳定 eventId、价格快照）")
        void joinGroup_groupSuccess_recordsOutboxEvent() {
            JoinGroupRequest request = new JoinGroupRequest(GROUP_ORDER_ID, ACTIVITY_ID);
            GroupOrder full = pendingGroup();
            full.setCurrentNumber(3);
            when(groupOrderMapper.selectById(GROUP_ORDER_ID)).thenReturn(pendingGroup(), full);
            when(groupOrderMapper.markSuccess(eq(GROUP_ORDER_ID), any())).thenReturn(1);

            GroupOrderDTO result = groupActivityService.joinGroup(USER_ID, request);

            assertThat(result).isNotNull();
            ArgumentCaptor<com.cloudmart.common.async.EventEnvelope> captor =
                    ArgumentCaptor.forClass(com.cloudmart.common.async.EventEnvelope.class);
            verify(outboxService).record(captor.capture());
            com.cloudmart.common.async.EventEnvelope envelope = captor.getValue();
            assertThat(envelope.eventType()).isEqualTo("GROUP_SUCCESS");
            assertThat(envelope.eventId()).isEqualTo("group-success-" + GROUP_ORDER_ID);
            assertThat(envelope.aggregateId()).isEqualTo(String.valueOf(GROUP_ORDER_ID));
            assertThat(envelope.payload()).contains("\"groupPrice\":\"99.00\"");
            assertThat(envelope.payload()).contains("\"memberUserIds\"");
        }

        @Test
        @DisplayName("T10：成团 CAS 失败（超时方已过期）→ 不发事件，正常返回")
        void joinGroup_successCasLost_noEvent() {
            JoinGroupRequest request = new JoinGroupRequest(GROUP_ORDER_ID, ACTIVITY_ID);
            when(groupOrderMapper.markSuccess(eq(GROUP_ORDER_ID), any())).thenReturn(0);

            GroupOrderDTO result = groupActivityService.joinGroup(USER_ID, request);

            assertThat(result).isNotNull();
            verify(outboxService, never()).record(any());
        }
    }

    @Nested
    @DisplayName("handleGroupExpiration")
    class ExpirationTests {

        @Test
        @DisplayName("T10：到期组 CAS 过期 + 成员标 EXPIRED（释放权益，不标 REFUNDED）+ 清理投影")
        void handleGroupExpiration_expiresAndReleases() {
            GroupOrder due = pendingGroup();
            due.setExpireTime(LocalDateTime.now().minusMinutes(5));
            when(groupOrderMapper.selectList(any())).thenReturn(List.of(due));
            when(groupOrderMapper.markExpired(GROUP_ORDER_ID)).thenReturn(1);
            when(groupOrderMapper.markMembersExpired(GROUP_ORDER_ID)).thenReturn(2);

            groupActivityService.handleGroupExpiration();

            verify(groupOrderMapper).markExpired(GROUP_ORDER_ID);
            verify(groupOrderMapper).markMembersExpired(GROUP_ORDER_ID);
            verify(redisTemplate).delete("marketing:group:" + GROUP_ORDER_ID);
            verify(redisTemplate).delete("marketing:group_users:" + GROUP_ORDER_ID);
        }

        @Test
        @DisplayName("T10：过期 CAS 失败（已 concurrently 成团）→ 不动成员不清理")
        void handleGroupExpiration_casLost_noSideEffects() {
            GroupOrder due = pendingGroup();
            due.setExpireTime(LocalDateTime.now().minusMinutes(5));
            when(groupOrderMapper.selectList(any())).thenReturn(List.of(due));
            when(groupOrderMapper.markExpired(GROUP_ORDER_ID)).thenReturn(0);

            groupActivityService.handleGroupExpiration();

            verify(groupOrderMapper, never()).markMembersExpired(any());
            verify(redisTemplate, never()).delete(anyString());
        }

        @Test
        @DisplayName("T10：无到期组 → 空转")
        void handleGroupExpiration_noDueGroups_noop() {
            when(groupOrderMapper.selectList(any())).thenReturn(List.of());

            groupActivityService.handleGroupExpiration();

            verify(groupOrderMapper, never()).markExpired(any());
        }
    }

    @Nested
    @DisplayName("getActivity")
    class GetActivityTests {

        @Test
        @DisplayName("should return activity DTO when found")
        void getActivity_success() {
            GroupActivityDTO dto = new GroupActivityDTO(
                    ACTIVITY_ID, "Test Group", "desc", PRODUCT_ID, SKU_ID,
                    new BigDecimal("199.00"), new BigDecimal("99.00"),
                    3, 10, 0, 1,
                    LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(24),
                    "ENABLED", LocalDateTime.now()
            );
            when(converter.toDTO(enabledActivity)).thenReturn(dto);

            GroupActivityDTO result = groupActivityService.getActivity(ACTIVITY_ID);

            assertThat(result.id()).isEqualTo(ACTIVITY_ID);
        }

        @Test
        @DisplayName("should throw when activity not found")
        void getActivity_notFound_throwsException() {
            when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(null);

            assertThatThrownBy(() -> groupActivityService.getActivity(ACTIVITY_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("ACTIVITY_NOT_FOUND");
        }
    }
}
