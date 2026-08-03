package com.cloudmart.marketing.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.marketing.converter.MarketingConverter;
import com.cloudmart.marketing.dto.GroupActivityDTO;
import com.cloudmart.marketing.dto.GroupOrderDTO;
import com.cloudmart.marketing.dto.JoinGroupRequest;
import com.cloudmart.marketing.entity.GroupActivity;
import com.cloudmart.marketing.entity.GroupMember;
import com.cloudmart.marketing.entity.GroupOrder;
import com.cloudmart.marketing.mq.MarketingMessageProducer;
import com.cloudmart.marketing.repository.GroupActivityMapper;
import com.cloudmart.marketing.repository.GroupMemberMapper;
import com.cloudmart.marketing.repository.GroupOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
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
    private MarketingMessageProducer messageProducer;

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
                activityMapper, groupOrderMapper, memberMapper, converter, redisTemplate, messageProducer);

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
    }

    @Nested
    @DisplayName("openGroup (joinGroup with no groupOrderId)")
    class OpenGroupTests {

        @Test
        @DisplayName("should open a new group successfully")
        void openGroup_success() {
            JoinGroupRequest request = new JoinGroupRequest(null, ACTIVITY_ID);

            when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(enabledActivity);
            when(groupOrderMapper.insert(any(GroupOrder.class))).thenAnswer(invocation -> {
                GroupOrder go = invocation.getArgument(0);
                go.setId(GROUP_ORDER_ID);
                return 1;
            });
            when(activityMapper.updateById(any(GroupActivity.class))).thenReturn(1);
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);

            List<Object> luaResult = List.of(0);
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(luaResult);

            when(memberMapper.insert(any(GroupMember.class))).thenReturn(1);
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.get(anyString(), eq("currentNumber"))).thenReturn("1");
            when(groupOrderMapper.updateById(any(GroupOrder.class))).thenReturn(1);
            when(memberMapper.selectList(any())).thenReturn(Collections.emptyList());

            GroupOrderDTO result = groupActivityService.joinGroup(USER_ID, request);

            assertThat(result).isNotNull();
            verify(groupOrderMapper).insert(any(GroupOrder.class));
            verify(memberMapper).insert(any(GroupMember.class));
        }
    }

    @Nested
    @DisplayName("joinGroup")
    class JoinGroupTests {

        @Test
        @DisplayName("should throw when activity not found")
        void joinGroup_activityNotFound_throwsException() {
            JoinGroupRequest request = new JoinGroupRequest(null, ACTIVITY_ID);
            when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(null);

            assertThatThrownBy(() -> groupActivityService.joinGroup(USER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("ACTIVITY_NOT_FOUND");
        }

        @Test
        @DisplayName("should throw when activity is not enabled")
        void joinGroup_activityNotEnabled_throwsException() {
            JoinGroupRequest request = new JoinGroupRequest(null, ACTIVITY_ID);
            enabledActivity.setStatus("DISABLED");
            when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(enabledActivity);

            assertThatThrownBy(() -> groupActivityService.joinGroup(USER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("ACTIVITY_NOT_ENABLED");
        }

        @Test
        @DisplayName("should throw when activity is expired")
        void joinGroup_activityExpired_throwsException() {
            JoinGroupRequest request = new JoinGroupRequest(null, ACTIVITY_ID);
            enabledActivity.setEndTime(LocalDateTime.now().minusHours(1));
            when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(enabledActivity);

            assertThatThrownBy(() -> groupActivityService.joinGroup(USER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("ACTIVITY_NOT_IN_PROGRESS");
        }

        @Test
        @DisplayName("should throw when max groups reached")
        void joinGroup_maxGroupsReached_throwsException() {
            JoinGroupRequest request = new JoinGroupRequest(null, ACTIVITY_ID);
            enabledActivity.setMaxGroups(2);
            enabledActivity.setCurrentGroups(2);
            when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(enabledActivity);

            assertThatThrownBy(() -> groupActivityService.joinGroup(USER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("MAX_GROUPS_REACHED");
        }

        @Test
        @DisplayName("should throw when group order not found for joining")
        void joinGroup_groupNotFound_throwsException() {
            JoinGroupRequest request = new JoinGroupRequest(GROUP_ORDER_ID, ACTIVITY_ID);
            when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(enabledActivity);
            when(groupOrderMapper.selectById(GROUP_ORDER_ID)).thenReturn(null);

            assertThatThrownBy(() -> groupActivityService.joinGroup(USER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("GROUP_NOT_FOUND");
        }

        @Test
        @DisplayName("should throw when Lua returns -1 (group not pending)")
        void joinGroup_groupNotPending_throwsException() {
            JoinGroupRequest request = new JoinGroupRequest(null, ACTIVITY_ID);
            when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(enabledActivity);
            when(groupOrderMapper.insert(any(GroupOrder.class))).thenAnswer(invocation -> {
                GroupOrder go = invocation.getArgument(0);
                go.setId(GROUP_ORDER_ID);
                return 1;
            });
            when(activityMapper.updateById(any(GroupActivity.class))).thenReturn(1);
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(List.of(-1));

            assertThatThrownBy(() -> groupActivityService.joinGroup(USER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("GROUP_NOT_PENDING");
        }

        @Test
        @DisplayName("should throw when Lua returns -2 (user already in group)")
        void joinGroup_userAlreadyInGroup_throwsException() {
            JoinGroupRequest request = new JoinGroupRequest(null, ACTIVITY_ID);
            when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(enabledActivity);
            when(groupOrderMapper.insert(any(GroupOrder.class))).thenAnswer(invocation -> {
                GroupOrder go = invocation.getArgument(0);
                go.setId(GROUP_ORDER_ID);
                return 1;
            });
            when(activityMapper.updateById(any(GroupActivity.class))).thenReturn(1);
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(List.of(-2));

            assertThatThrownBy(() -> groupActivityService.joinGroup(USER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("USER_ALREADY_IN_GROUP");
        }

        @Test
        @DisplayName("should throw when Lua returns -3 (user already joined activity)")
        void joinGroup_userAlreadyJoinedActivity_throwsException() {
            JoinGroupRequest request = new JoinGroupRequest(null, ACTIVITY_ID);
            when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(enabledActivity);
            when(groupOrderMapper.insert(any(GroupOrder.class))).thenAnswer(invocation -> {
                GroupOrder go = invocation.getArgument(0);
                go.setId(GROUP_ORDER_ID);
                return 1;
            });
            when(activityMapper.updateById(any(GroupActivity.class))).thenReturn(1);
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(List.of(-3));

            assertThatThrownBy(() -> groupActivityService.joinGroup(USER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("USER_ALREADY_JOINED_ACTIVITY");
        }

        @Test
        @DisplayName("should throw when Lua returns -4 (group full)")
        void joinGroup_groupFull_throwsException() {
            JoinGroupRequest request = new JoinGroupRequest(null, ACTIVITY_ID);
            when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(enabledActivity);
            when(groupOrderMapper.insert(any(GroupOrder.class))).thenAnswer(invocation -> {
                GroupOrder go = invocation.getArgument(0);
                go.setId(GROUP_ORDER_ID);
                return 1;
            });
            when(activityMapper.updateById(any(GroupActivity.class))).thenReturn(1);
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(List.of(-4));

            assertThatThrownBy(() -> groupActivityService.joinGroup(USER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("GROUP_FULL");
        }

        @Test
        @DisplayName("should send MQ when group reaches target (Lua returns 1)")
        void joinGroup_groupSuccess_sendsMQ() {
            JoinGroupRequest request = new JoinGroupRequest(null, ACTIVITY_ID);
            when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(enabledActivity);
            when(groupOrderMapper.insert(any(GroupOrder.class))).thenAnswer(invocation -> {
                GroupOrder go = invocation.getArgument(0);
                go.setId(GROUP_ORDER_ID);
                return 1;
            });
            when(activityMapper.updateById(any(GroupActivity.class))).thenReturn(1);
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(List.of(1));

            when(memberMapper.insert(any(GroupMember.class))).thenReturn(1);
            when(hashOperations.get(anyString(), eq("currentNumber"))).thenReturn("3");
            when(groupOrderMapper.updateById(any(GroupOrder.class))).thenReturn(1);
            when(memberMapper.selectList(any())).thenReturn(List.of());

            GroupOrderDTO result = groupActivityService.joinGroup(USER_ID, request);

            assertThat(result).isNotNull();
            verify(messageProducer).sendGroupSuccessMessage(
                    eq(GROUP_ORDER_ID), eq(ACTIVITY_ID),
                    eq(PRODUCT_ID), eq(SKU_ID), anyList()
            );
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
            when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(enabledActivity);
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
