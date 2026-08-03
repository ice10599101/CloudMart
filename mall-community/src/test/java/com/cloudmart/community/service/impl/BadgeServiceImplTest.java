package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.community.dto.CreateBadgeRequest;
import com.cloudmart.community.dto.UpdateBadgeRequest;
import com.cloudmart.community.entity.Badge;
import com.cloudmart.community.entity.UserBadge;
import com.cloudmart.community.repository.BadgeMapper;
import com.cloudmart.community.repository.UserBadgeMapper;
import com.cloudmart.community.vo.BadgeVO;
import com.cloudmart.common.exception.BusinessException;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BadgeServiceImplTest {

    @Mock
    private BadgeMapper badgeMapper;

    @Mock
    private UserBadgeMapper userBadgeMapper;

    private BadgeServiceImpl badgeService;

    private static final Long BADGE_ID = 1L;
    private static final Long USER_ID = 100L;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant badgeAssistant = new MapperBuilderAssistant(configuration, "");
        badgeAssistant.setCurrentNamespace("com.cloudmart.community.repository.BadgeMapper");
        TableInfoHelper.initTableInfo(badgeAssistant, Badge.class);
        MapperBuilderAssistant userBadgeAssistant = new MapperBuilderAssistant(configuration, "");
        userBadgeAssistant.setCurrentNamespace("com.cloudmart.community.repository.UserBadgeMapper");
        TableInfoHelper.initTableInfo(userBadgeAssistant, UserBadge.class);
    }

    @BeforeEach
    void setUp() {
        badgeService = new BadgeServiceImpl(badgeMapper, userBadgeMapper);
    }

    private Badge buildBadge() {
        Badge badge = new Badge();
        badge.setId(BADGE_ID);
        badge.setName("Expert");
        badge.setIcon("expert-icon");
        badge.setDescription("Expert contributor");
        badge.setCondition("100 posts");
        badge.setLevel(3);
        badge.setStatus(1);
        return badge;
    }

    private UserBadge buildUserBadge() {
        UserBadge userBadge = new UserBadge();
        userBadge.setId(1L);
        userBadge.setUserId(USER_ID);
        userBadge.setBadgeId(BADGE_ID);
        return userBadge;
    }

    @Nested
    @DisplayName("createBadge")
    class CreateBadgeTests {

        @Test
        @DisplayName("should create badge and return BadgeVO")
        void createBadge_success() {
            CreateBadgeRequest request = new CreateBadgeRequest(
                    "Expert", "expert-icon", "Expert contributor", "100 posts", 3);
            when(badgeMapper.insert(any(Badge.class))).thenAnswer(invocation -> {
                Badge badge = invocation.getArgument(0);
                badge.setId(BADGE_ID);
                return 1;
            });

            BadgeVO result = badgeService.createBadge(request);

            assertThat(result).isNotNull();
            assertThat(result.name()).isEqualTo("Expert");
            assertThat(result.icon()).isEqualTo("expert-icon");
            assertThat(result.description()).isEqualTo("Expert contributor");
            assertThat(result.condition()).isEqualTo("100 posts");
            assertThat(result.level()).isEqualTo(3);
            assertThat(result.status()).isEqualTo(1);
            verify(badgeMapper).insert(any(Badge.class));
        }
    }

    @Nested
    @DisplayName("updateBadge")
    class UpdateBadgeTests {

        @Test
        @DisplayName("should update badge fields and return BadgeVO")
        void updateBadge_success() {
            Badge badge = buildBadge();
            when(badgeMapper.selectById(BADGE_ID)).thenReturn(badge);

            UpdateBadgeRequest request = new UpdateBadgeRequest(
                    "Master", "master-icon", "Master contributor", "500 posts", 5, 1);
            BadgeVO result = badgeService.updateBadge(BADGE_ID, request);

            assertThat(result).isNotNull();
            assertThat(badge.getName()).isEqualTo("Master");
            assertThat(badge.getIcon()).isEqualTo("master-icon");
            assertThat(badge.getLevel()).isEqualTo(5);
            verify(badgeMapper).updateById(badge);
        }

        @Test
        @DisplayName("should throw when badge not found")
        void updateBadge_notFound_throwsException() {
            when(badgeMapper.selectById(BADGE_ID)).thenReturn(null);

            UpdateBadgeRequest request = new UpdateBadgeRequest("Name", null, null, null, null, null);

            assertThatThrownBy(() -> badgeService.updateBadge(BADGE_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo("BADGE_NOT_FOUND");
                    });

            verify(badgeMapper, never()).updateById(any(Badge.class));
        }

        @Test
        @DisplayName("should only update non-null fields")
        void updateBadge_partialUpdate() {
            Badge badge = buildBadge();
            when(badgeMapper.selectById(BADGE_ID)).thenReturn(badge);

            UpdateBadgeRequest request = new UpdateBadgeRequest(null, "new-icon", null, null, null, null);
            badgeService.updateBadge(BADGE_ID, request);

            assertThat(badge.getName()).isEqualTo("Expert");
            assertThat(badge.getIcon()).isEqualTo("new-icon");
            assertThat(badge.getLevel()).isEqualTo(3);
            verify(badgeMapper).updateById(badge);
        }
    }

    @Nested
    @DisplayName("deleteBadge")
    class DeleteBadgeTests {

        @Test
        @DisplayName("should delete badge and associated user badges")
        void deleteBadge_success() {
            badgeService.deleteBadge(BADGE_ID);

            verify(badgeMapper).deleteById(anyLong());
            verify(userBadgeMapper).delete(any());
        }
    }

    @Nested
    @DisplayName("grantBadge")
    class GrantBadgeTests {

        @Test
        @DisplayName("should grant badge to user")
        void grantBadge_success() {
            when(userBadgeMapper.selectCount(any())).thenReturn(0L);

            badgeService.grantBadge(USER_ID, BADGE_ID);

            verify(userBadgeMapper).insert(any(UserBadge.class));
        }

        @Test
        @DisplayName("should throw when badge already granted")
        void grantBadge_alreadyGranted_throwsException() {
            when(userBadgeMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> badgeService.grantBadge(USER_ID, BADGE_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo("BADGE_ALREADY_GRANTED");
                    });

            verify(userBadgeMapper, never()).insert(any(UserBadge.class));
        }
    }

    @Nested
    @DisplayName("revokeBadge")
    class RevokeBadgeTests {

        @Test
        @DisplayName("should revoke badge from user")
        void revokeBadge_success() {
            badgeService.revokeBadge(USER_ID, BADGE_ID);

            verify(userBadgeMapper).delete(any());
        }
    }

    @Nested
    @DisplayName("getUserBadges")
    class GetUserBadgesTests {

        @Test
        @DisplayName("should return badge list for user")
        void getUserBadges_success() {
            UserBadge userBadge = buildUserBadge();
            Badge badge = buildBadge();
            when(userBadgeMapper.selectList(any())).thenReturn(List.of(userBadge));
            when(badgeMapper.selectBatchIds(List.of(BADGE_ID))).thenReturn(List.of(badge));

            List<BadgeVO> result = badgeService.getUserBadges(USER_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).name()).isEqualTo("Expert");
        }

        @Test
        @DisplayName("should return empty list when user has no badges")
        void getUserBadges_empty() {
            when(userBadgeMapper.selectList(any())).thenReturn(Collections.emptyList());

            List<BadgeVO> result = badgeService.getUserBadges(USER_ID);

            assertThat(result).isEmpty();
            verify(badgeMapper, never()).selectBatchIds(any());
        }
    }

    @Nested
    @DisplayName("listBadges")
    class ListBadgesTests {

        @Test
        @DisplayName("should return paginated badges")
        void listBadges_success() {
            Badge badge = buildBadge();
            Page<Badge> badgePage = new Page<>(1, 10, 1);
            badgePage.setRecords(List.of(badge));
            when(badgeMapper.selectPage(any(Page.class), any())).thenReturn(badgePage);

            Page<BadgeVO> result = badgeService.listBadges(1, 10);

            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getTotal()).isEqualTo(1);
            assertThat(result.getRecords().get(0).name()).isEqualTo("Expert");
        }
    }
}
