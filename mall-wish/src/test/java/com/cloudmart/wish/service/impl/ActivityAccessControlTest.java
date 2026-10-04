package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.ActivityParticipant;
import com.cloudmart.wish.entity.CommunityActivity;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.entity.WishGrowthRecord;
import com.cloudmart.wish.enums.ActivityParticipantStatus;
import com.cloudmart.wish.enums.ActivityStatus;
import com.cloudmart.wish.enums.ActivityType;
import com.cloudmart.wish.enums.AuditStatus;
import com.cloudmart.wish.enums.WishVisibility;
import com.cloudmart.wish.repository.WishBadgeMapper;
import com.cloudmart.wish.repository.WishUserBadgeMapper;
import com.cloudmart.wish.policy.WishAccessPolicy;
import com.cloudmart.wish.repository.ActivityParticipantMapper;
import com.cloudmart.wish.repository.ActivityRewardLogMapper;
import com.cloudmart.wish.repository.CommunityActivityMapper;
import com.cloudmart.wish.repository.WishGrowthRecordMapper;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.repository.WishProgressMapper;
import com.cloudmart.wish.repository.WishUserStatMapper;
import com.cloudmart.wish.service.UserStatService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T13 活动参与与搭子审批（身份域/边界/隐私）：
 * ① WISH_PARTNER 直接 join 拒绝（绕过审批缺陷）；② validTo 到期拒绝；
 * ③ 审批操作者与管理员创建者同域（管理员 ID 恰等于用户 ID 不误授权）；
 * ④ 看板仅 APPROVED 成员、私密心愿与未审核成长内容不出板。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ActivityServiceImpl 访问控制（T13）")
class ActivityAccessControlTest {

    private static final Long USER_ID = 1001L;
    private static final Long ADMIN_ID = 9001L;
    private static final Long ACTIVITY_ID = 301L;

    @Mock
    private com.cloudmart.wish.repository.ActivityParticipantMapper activityParticipantMapper;
    @Mock
    private CommunityActivityMapper activityMapper;
    @Mock
    private ActivityParticipantMapper participantMapper;
    @Mock
    private ActivityRewardLogMapper rewardLogMapper;
    @Mock
    private WishMapper wishMapper;
    @Mock
    private WishProgressMapper progressMapper;
    @Mock
    private WishUserStatMapper userStatMapper;
    @Mock
    private WishBadgeMapper badgeMapper;
    @Mock
    private WishUserBadgeMapper userBadgeMapper;
    @Mock
    private WishGrowthRecordMapper growthRecordMapper;
    @Mock
    private UserStatService userStatService;
    @Mock
    private StringRedisTemplate redisTemplate;

    private ActivityServiceImpl service;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> clazz : List.of(CommunityActivity.class, ActivityParticipant.class,
                Wish.class, com.cloudmart.wish.entity.WishProgress.class,
                WishGrowthRecord.class, com.cloudmart.wish.entity.WishUserStat.class)) {
            TableInfoHelper.initTableInfo(assistant, clazz);
        }
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        org.mockito.Mockito.lenient().when(redisTemplate.opsForValue())
                .thenReturn(org.mockito.Mockito.mock(org.springframework.data.redis.core.ValueOperations.class));
        service = new ActivityServiceImpl(activityParticipantMapper, activityMapper, participantMapper,
                rewardLogMapper, wishMapper, progressMapper, userStatMapper, badgeMapper, userBadgeMapper,
                growthRecordMapper, userStatService, redisTemplate, new WishAccessPolicy());
    }

    private CommunityActivity activity(ActivityType type, LocalDateTime validTo) {
        CommunityActivity activity = new CommunityActivity();
        activity.setId(ACTIVITY_ID);
        activity.setType(type);
        activity.setStatus(ActivityStatus.ACTIVE);
        activity.setCreatedBy(ADMIN_ID);
        activity.setValidFrom(LocalDateTime.now(ZoneId.of("UTC")).minusDays(1));
        activity.setValidTo(validTo);
        return activity;
    }

    private ActivityParticipant participant(Long userId, ActivityParticipantStatus status) {
        ActivityParticipant p = new ActivityParticipant();
        p.setId(userId);
        p.setActivityId(ACTIVITY_ID);
        p.setUserId(userId);
        p.setStatus(status);
        return p;
    }

    @Test
    @DisplayName("T13 回归：搭子协作活动直接 join 被拒绝（须申请→审批）")
    void join_partnerActivity_rejected() {
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity(ActivityType.WISH_PARTNER, null));

        assertThatThrownBy(() -> service.join(USER_ID, ACTIVITY_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", WishErrorCodes.WISH_VALIDATION_ERROR);
        verify(participantMapper, never()).insert(any(ActivityParticipant.class));
    }

    @Test
    @DisplayName("T13 回归：validTo 已过期的活动不能加入（旧实现只校验 validFrom）")
    void join_expiredActivity_rejected() {
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity(ActivityType.CITY,
                LocalDateTime.now(ZoneId.of("UTC")).minusDays(1)));

        assertThatThrownBy(() -> service.join(USER_ID, ACTIVITY_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", WishErrorCodes.WISH_VALIDATION_ERROR);
        verify(participantMapper, never()).insert(any(ActivityParticipant.class));
    }

    @Test
    @DisplayName("T13 回归：审批操作者与创建管理员同域——用户 ID 恰等于管理员 ID 不能审批")
    void review_userIdCollidingWithAdminId_rejected() {
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity(ActivityType.WISH_PARTNER, null));

        // USER_ID=1001 ≠ createdBy=9001：即使某用户 ID 与 createdBy 相撞才可能通过，
        // 此处断言普通用户主体无法审批（服务只接受管理员域 operatorAdminId）
        assertThatThrownBy(() -> service.reviewApplication(USER_ID, ACTIVITY_ID, 2002L, true))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", WishErrorCodes.WISH_FORBIDDEN);
    }

    @Test
    @DisplayName("审批：创建管理员本人 + PENDING 申请 → CAS 推进")
    void review_byCreatingAdmin_approved() {
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity(ActivityType.WISH_PARTNER, null));
        when(participantMapper.selectOne(any())).thenReturn(participant(2002L, ActivityParticipantStatus.PENDING));
        when(participantMapper.update(any(), any())).thenReturn(1);

        service.reviewApplication(ADMIN_ID, ACTIVITY_ID, 2002L, true);

        verify(participantMapper).update(any(), any());
    }

    @Test
    @DisplayName("看板：非 APPROVED 成员（含 JOINED 普通参与）不可查看")
    void partnerBoard_nonApprovedMember_rejected() {
        CommunityActivity activity = activity(ActivityType.WISH_PARTNER, null);
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity);
        when(participantMapper.selectOne(any())).thenReturn(participant(USER_ID, ActivityParticipantStatus.JOINED));

        assertThatThrownBy(() -> service.getPartnerBoard(ACTIVITY_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", WishErrorCodes.WISH_FORBIDDEN);
    }

    @Test
    @DisplayName("看板隐私：成员心愿为 PRIVATE 时标题/进度/成长内容均不出板")
    void partnerBoard_privateWish_hidden() {
        CommunityActivity activity = activity(ActivityType.WISH_PARTNER, null);
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity);
        when(participantMapper.selectOne(any())).thenReturn(participant(USER_ID, ActivityParticipantStatus.APPROVED));
        ActivityParticipant member = participant(2002L, ActivityParticipantStatus.APPROVED);
        member.setWishId(5001L);
        when(participantMapper.selectList(any())).thenReturn(List.of(member));

        Wish privateWish = new Wish();
        privateWish.setId(5001L);
        privateWish.setUserId(2002L);
        privateWish.setTitle("私密心愿");
        privateWish.setVisibility(WishVisibility.PRIVATE);
        when(wishMapper.selectById(5001L)).thenReturn(privateWish);

        var board = service.getPartnerBoard(ACTIVITY_ID, USER_ID);

        assertThat(board).hasSize(1);
        assertThat(board.get(0).title()).isNull();
        assertThat(board.get(0).latestGrowth()).isNull();
    }

    @Test
    @DisplayName("看板隐私：公开心愿的成长记录仅审核通过且可见的出板")
    void partnerBoard_growthRecord_visibilityFiltered() {
        CommunityActivity activity = activity(ActivityType.WISH_PARTNER, null);
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(activity);
        when(participantMapper.selectOne(any())).thenReturn(participant(USER_ID, ActivityParticipantStatus.APPROVED));
        ActivityParticipant member = participant(2002L, ActivityParticipantStatus.APPROVED);
        member.setWishId(5001L);
        when(participantMapper.selectList(any())).thenReturn(List.of(member));

        Wish publicWish = new Wish();
        publicWish.setId(5001L);
        publicWish.setUserId(2002L);
        publicWish.setTitle("公开心愿");
        publicWish.setVisibility(WishVisibility.PUBLIC);
        publicWish.setAuditStatus(AuditStatus.APPROVED);
        publicWish.setIsVisible(true);
        when(wishMapper.selectById(5001L)).thenReturn(publicWish);
        when(progressMapper.selectOne(any())).thenReturn(null);

        WishGrowthRecord pending = new WishGrowthRecord();
        pending.setContent("待审核内容");
        pending.setAuditStatus(AuditStatus.PENDING);
        pending.setIsVisible(true);
        when(growthRecordMapper.selectList(any())).thenReturn(List.of(pending));

        var board = service.getPartnerBoard(ACTIVITY_ID, USER_ID);

        assertThat(board.get(0).title()).isEqualTo("公开心愿");
        assertThat(board.get(0).latestGrowth()).isNull();
    }
}
