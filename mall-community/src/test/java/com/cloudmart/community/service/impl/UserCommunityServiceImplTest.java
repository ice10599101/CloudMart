package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.community.entity.Post;
import com.cloudmart.community.entity.PostCollection;
import com.cloudmart.community.repository.PostCollectionMapper;
import com.cloudmart.community.repository.PostMapper;
import com.cloudmart.community.service.BadgeService;
import com.cloudmart.community.service.UserEnrichmentService;
import com.cloudmart.community.service.UserFollowService;
import com.cloudmart.community.vo.BadgeVO;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserCommunityServiceImplTest {

    @Mock
    private PostMapper postMapper;

    @Mock
    private PostCollectionMapper postCollectionMapper;

    @Mock
    private BadgeService badgeService;

    @Mock
    private UserFollowService userFollowService;

    @Mock
    private UserEnrichmentService userEnrichmentService;

    private UserCommunityServiceImpl userCommunityService;

    private static final Long USER_ID = 1L;
    private static final Long CURRENT_USER_ID = 2L;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Post.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), PostCollection.class);
    }

    @BeforeEach
    void setUp() {
        userCommunityService = new UserCommunityServiceImpl(
                postMapper, postCollectionMapper, badgeService, userFollowService, userEnrichmentService
        );
    }

    private void mockUserProfileDependencies() {
        when(postMapper.selectCount(any())).thenReturn(5L);
        when(userFollowService.getFollowCount(USER_ID)).thenReturn(3L);
        when(userFollowService.getFollowerCount(USER_ID)).thenReturn(10L);
        when(postCollectionMapper.selectCount(any())).thenReturn(8L);
        when(badgeService.getUserBadges(USER_ID)).thenReturn(List.of(
                new BadgeVO(1L, "活跃之星", "star.png", "活跃用户", "posts>=10", 1, 1, LocalDateTime.now())
        ));
        when(userEnrichmentService.getSingleUser(USER_ID)).thenReturn(
                new UserEnrichmentService.UserInfo(USER_ID, "testUser", "avatar.png", "testuser", "hello world")
        );
    }

    @Nested
    @DisplayName("getUserProfile")
    class GetUserProfileTests {

        @Test
        @DisplayName("should return complete user profile with all aggregated data")
        void getUserProfile_returnsCompleteProfile() {
            mockUserProfileDependencies();

            UserCommunityVO result = userCommunityService.getUserProfile(USER_ID, CURRENT_USER_ID);

            assertThat(result).isNotNull();
            assertThat(result.userId()).isEqualTo(USER_ID);
            assertThat(result.nickname()).isEqualTo("testUser");
            assertThat(result.avatar()).isEqualTo("avatar.png");
            assertThat(result.signature()).isEqualTo("hello world");
            assertThat(result.postCount()).isEqualTo(5L);
            assertThat(result.followCount()).isEqualTo(3L);
            assertThat(result.followerCount()).isEqualTo(10L);
            assertThat(result.collectCount()).isEqualTo(8L);
        }

        @Test
        @DisplayName("should include badges in profile")
        void getUserProfile_includesBadges() {
            mockUserProfileDependencies();

            UserCommunityVO result = userCommunityService.getUserProfile(USER_ID, CURRENT_USER_ID);

            assertThat(result.badges()).isNotEmpty();
            assertThat(result.badges()).hasSize(1);
            assertThat(result.badges().get(0).name()).isEqualTo("活跃之星");
        }

        @Test
        @DisplayName("should return zero counts when user has no activity")
        void getUserProfile_noActivity_returnsZeroCounts() {
            when(postMapper.selectCount(any())).thenReturn(0L);
            when(userFollowService.getFollowCount(USER_ID)).thenReturn(0L);
            when(userFollowService.getFollowerCount(USER_ID)).thenReturn(0L);
            when(postCollectionMapper.selectCount(any())).thenReturn(0L);
            when(badgeService.getUserBadges(USER_ID)).thenReturn(List.of());
            when(userEnrichmentService.getSingleUser(USER_ID)).thenReturn(
                    new UserEnrichmentService.UserInfo(USER_ID, "newUser", null, null, null)
            );

            UserCommunityVO result = userCommunityService.getUserProfile(USER_ID, CURRENT_USER_ID);

            assertThat(result.postCount()).isEqualTo(0L);
            assertThat(result.followCount()).isEqualTo(0L);
            assertThat(result.followerCount()).isEqualTo(0L);
            assertThat(result.collectCount()).isEqualTo(0L);
            assertThat(result.badges()).isEmpty();
        }

        @Test
        @DisplayName("should return null isFollowed when querying own profile without current user context")
        void getUserProfile_ownProfile_isFollowedNull() {
            mockUserProfileDependencies();

            UserCommunityVO result = userCommunityService.getUserProfile(USER_ID, null);

            assertThat(result.isFollowed()).isNull();
        }

        @Test
        @DisplayName("should count only published posts (status=1)")
        void getUserProfile_countsOnlyPublishedPosts() {
            when(postMapper.selectCount(any())).thenReturn(2L);
            when(userFollowService.getFollowCount(USER_ID)).thenReturn(0L);
            when(userFollowService.getFollowerCount(USER_ID)).thenReturn(0L);
            when(postCollectionMapper.selectCount(any())).thenReturn(0L);
            when(badgeService.getUserBadges(USER_ID)).thenReturn(List.of());
            when(userEnrichmentService.getSingleUser(USER_ID)).thenReturn(
                    new UserEnrichmentService.UserInfo(USER_ID, "user", null, null, null)
            );

            UserCommunityVO result = userCommunityService.getUserProfile(USER_ID, CURRENT_USER_ID);

            assertThat(result.postCount()).isEqualTo(2L);
        }
    }
}
