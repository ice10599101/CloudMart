package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.community.entity.UserFollow;
import com.cloudmart.community.mq.CommunityEventProducer;
import com.cloudmart.community.repository.UserFollowMapper;
import com.cloudmart.community.service.GrowthService;
import com.cloudmart.community.service.UserEnrichmentService;
import com.cloudmart.community.vo.UserCommunityVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserFollowServiceImplTest {

    @Mock
    private UserFollowMapper userFollowMapper;

    @Mock
    private CommunityEventProducer communityEventProducer;

    @Mock
    private GrowthService growthService;

    @Mock
    private UserEnrichmentService userEnrichmentService;

    private UserFollowServiceImpl userFollowService;

    private static final Long USER_ID = 1L;
    private static final Long FOLLOWING_ID = 2L;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserFollow.class);
    }

    @BeforeEach
    void setUp() {
        userFollowService = new UserFollowServiceImpl(
                userFollowMapper, communityEventProducer, growthService, userEnrichmentService
        );
    }

    private UserFollow buildUserFollow() {
        UserFollow follow = new UserFollow();
        follow.setId(1L);
        follow.setFollowerId(USER_ID);
        follow.setFollowingId(FOLLOWING_ID);
        return follow;
    }

    @Nested
    @DisplayName("follow")
    class FollowTests {

        @Test
        @DisplayName("should throw when following self")
        void follow_selfFollow_throwsException() {
            assertThatThrownBy(() -> userFollowService.follow(USER_ID, USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo("CANNOT_FOLLOW_SELF");
                    });

            verify(userFollowMapper, never()).insert(any(UserFollow.class));
        }

        @Test
        @DisplayName("should throw when already following")
        void follow_alreadyFollowing_throwsException() {
            when(userFollowMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> userFollowService.follow(USER_ID, FOLLOWING_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo("ALREADY_FOLLOWING");
                    });

            verify(userFollowMapper, never()).insert(any(UserFollow.class));
        }

        @Test
        @DisplayName("should insert follow record, add exp and publish event on new follow")
        void follow_newFollow_insertsAndTriggersSideEffects() {
            when(userFollowMapper.selectCount(any())).thenReturn(0L);

            userFollowService.follow(USER_ID, FOLLOWING_ID);

            verify(userFollowMapper).insert(any(UserFollow.class));
            verify(growthService).addExp(eq(FOLLOWING_ID), eq(15), eq("FOLLOW_RECEIVED"), eq(null), eq("获得新粉丝"));
            verify(communityEventProducer).publishFollowEvent(FOLLOWING_ID, USER_ID);
        }
    }

    @Nested
    @DisplayName("unfollow")
    class UnfollowTests {

        @Test
        @DisplayName("should delete follow record by wrapper")
        void unfollow_deletesFollow() {
            when(userFollowMapper.delete(any())).thenReturn(1);

            userFollowService.unfollow(USER_ID, FOLLOWING_ID);

            verify(userFollowMapper).delete(any());
        }
    }

    @Nested
    @DisplayName("isFollowing")
    class IsFollowingTests {

        @Test
        @DisplayName("should return true when follow exists")
        void isFollowing_exists_returnsTrue() {
            when(userFollowMapper.selectCount(any())).thenReturn(1L);

            boolean result = userFollowService.isFollowing(USER_ID, FOLLOWING_ID);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when follow does not exist")
        void isFollowing_notExists_returnsFalse() {
            when(userFollowMapper.selectCount(any())).thenReturn(0L);

            boolean result = userFollowService.isFollowing(USER_ID, FOLLOWING_ID);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("getFollowCount")
    class GetFollowCountTests {

        @Test
        @DisplayName("should return follow count from mapper")
        void getFollowCount_returnsCount() {
            when(userFollowMapper.selectCount(any())).thenReturn(5L);

            long result = userFollowService.getFollowCount(USER_ID);

            assertThat(result).isEqualTo(5L);
        }
    }

    @Nested
    @DisplayName("getFollowerCount")
    class GetFollowerCountTests {

        @Test
        @DisplayName("should return follower count from mapper")
        void getFollowerCount_returnsCount() {
            when(userFollowMapper.selectCount(any())).thenReturn(10L);

            long result = userFollowService.getFollowerCount(USER_ID);

            assertThat(result).isEqualTo(10L);
        }
    }

    @Nested
    @DisplayName("getFollowerList")
    class GetFollowerListTests {

        @Test
        @DisplayName("should return empty list when no followers")
        void getFollowerList_noFollowers_returnsEmpty() {
            when(userFollowMapper.selectList(any())).thenReturn(Collections.emptyList());

            List<UserCommunityVO> result = userFollowService.getFollowerList(USER_ID, null, 1, 10);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return follower VOs with correct isFollowed flag")
        void getFollowerList_hasFollowers_returnsMappedVOs() {
            UserFollow follow = buildUserFollow();
            when(userFollowMapper.selectList(any())).thenReturn(List.of(follow));
            when(userFollowMapper.selectCount(any())).thenReturn(1L);

            List<UserCommunityVO> result = userFollowService.getFollowerList(FOLLOWING_ID, USER_ID, 1, 10);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).userId()).isEqualTo(USER_ID);
            assertThat(result.get(0).isFollowed()).isTrue();
        }
    }

    @Nested
    @DisplayName("getFollowingList")
    class GetFollowingListTests {

        @Test
        @DisplayName("should return empty list when not following anyone")
        void getFollowingList_noFollowing_returnsEmpty() {
            when(userFollowMapper.selectList(any())).thenReturn(Collections.emptyList());

            List<UserCommunityVO> result = userFollowService.getFollowingList(USER_ID, null, 1, 10);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return following VOs with isFollowed always true")
        void getFollowingList_hasFollowing_returnsMappedVOs() {
            UserFollow follow = buildUserFollow();
            when(userFollowMapper.selectList(any())).thenReturn(List.of(follow));

            List<UserCommunityVO> result = userFollowService.getFollowingList(USER_ID, null, 1, 10);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).userId()).isEqualTo(FOLLOWING_ID);
            assertThat(result.get(0).isFollowed()).isTrue();
        }
    }

    @Nested
    @DisplayName("getRecommendedUsers")
    class GetRecommendedUsersTests {

        @Test
        @DisplayName("should return empty when no users in system and userId is null")
        void getRecommendedUsers_nullUserId_noData_returnsEmpty() {
            when(userFollowMapper.selectList(any())).thenReturn(Collections.emptyList());

            List<UserCommunityVO> result = userFollowService.getRecommendedUsers(null, 5);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return top followed users when userId is null")
        void getRecommendedUsers_nullUserId_returnsTopFollowed() {
            UserFollow follow = new UserFollow();
            follow.setFollowingId(FOLLOWING_ID);

            when(userFollowMapper.selectList(any())).thenReturn(List.of(follow));
            when(userEnrichmentService.batchGetUsers(any())).thenReturn(
                    java.util.Map.of(FOLLOWING_ID, new UserEnrichmentService.UserInfo(FOLLOWING_ID, "user2", "avatar", null, null))
            );

            List<UserCommunityVO> result = userFollowService.getRecommendedUsers(null, 5);

            assertThat(result).isNotEmpty();
            assertThat(result.get(0).userId()).isEqualTo(FOLLOWING_ID);
            assertThat(result.get(0).nickname()).isEqualTo("user2");
        }

        @Test
        @DisplayName("should return mutual-based recommendations for logged-in user")
        void getRecommendedUsers_withUserId_returnsMutualRecommendations() {
            UserFollow myFollow = new UserFollow();
            myFollow.setFollowerId(USER_ID);
            myFollow.setFollowingId(2L);

            UserFollow theirFollow = new UserFollow();
            theirFollow.setFollowerId(2L);
            theirFollow.setFollowingId(3L);

            when(userFollowMapper.selectList(any())).thenReturn(List.of(myFollow)).thenReturn(List.of(theirFollow));
            when(userEnrichmentService.batchGetUsers(any())).thenReturn(
                    java.util.Map.of(3L, new UserEnrichmentService.UserInfo(3L, "user3", "avatar3", null, null))
            );

            List<UserCommunityVO> result = userFollowService.getRecommendedUsers(USER_ID, 5);

            assertThat(result).isNotEmpty();
            assertThat(result.get(0).userId()).isEqualTo(3L);
        }
    }
}
