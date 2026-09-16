package com.cloudmart.community.service.impl;

import com.cloudmart.community.service.UserFollowService;
import com.cloudmart.community.service.UserSettingService;
import com.cloudmart.community.vo.PrivacyVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PrivacyServiceImplTest {

    private UserSettingService userSettingService;
    private UserFollowService userFollowService;
    private PrivacyServiceImpl privacyService;

    private static final String KEY_BIRTHDAY = "PRIVACY_BIRTHDAY_VISIBILITY";
    private static final String KEY_EMAIL = "PRIVACY_EMAIL_VISIBILITY";
    private static final String KEY_FOLLOWERS = "PRIVACY_FOLLOWERS_VISIBILITY";
    private static final String KEY_FOLLOWING = "PRIVACY_FOLLOWING_VISIBILITY";
    private static final String KEY_COLLECTIONS = "PRIVACY_COLLECTIONS_VISIBILITY";
    private static final String KEY_POSTS = "PRIVACY_POSTS_VISIBILITY";

    @BeforeEach
    void setUp() {
        userSettingService = mock(UserSettingService.class);
        userFollowService = mock(UserFollowService.class);
        privacyService = new PrivacyServiceImpl(userSettingService, userFollowService);
    }

    @Test
    @DisplayName("本人查看自身 -> 生日邮箱均可见且不查询关注关系")
    void selfView_AllVisible() {
        PrivacyVisibility result = privacyService.checkVisibility(1L, 1L);

        assertThat(result.birthdayVisible()).isTrue();
        assertThat(result.emailVisible()).isTrue();
        verifyNoInteractions(userFollowService);
    }

    @Test
    @DisplayName("陌生人查看，默认所有人可见 -> 生日邮箱可见")
    void stranger_DefaultAll_Visible() {
        when(userSettingService.getSetting(1L, KEY_BIRTHDAY, "ALL")).thenReturn("ALL");
        when(userSettingService.getSetting(1L, KEY_EMAIL, "ALL")).thenReturn("ALL");
        when(userFollowService.isFollowing(any(), any())).thenReturn(false);

        PrivacyVisibility result = privacyService.checkVisibility(2L, 1L);

        assertThat(result.birthdayVisible()).isTrue();
        assertThat(result.emailVisible()).isTrue();
    }

    @Test
    @DisplayName("陌生人查看，设置为仅自己 -> 生日邮箱均隐藏")
    void stranger_SelfSetting_Hidden() {
        when(userSettingService.getSetting(1L, KEY_BIRTHDAY, "ALL")).thenReturn("SELF");
        when(userSettingService.getSetting(1L, KEY_EMAIL, "ALL")).thenReturn("SELF");
        when(userFollowService.isFollowing(2L, 1L)).thenReturn(false);

        PrivacyVisibility result = privacyService.checkVisibility(2L, 1L);

        assertThat(result.birthdayVisible()).isFalse();
        assertThat(result.emailVisible()).isFalse();
    }

    @Test
    @DisplayName("互关好友查看，设置为互关可见 -> 生日邮箱可见")
    void mutual_MutualSetting_Visible() {
        when(userSettingService.getSetting(1L, KEY_BIRTHDAY, "ALL")).thenReturn("MUTUAL");
        when(userSettingService.getSetting(1L, KEY_EMAIL, "ALL")).thenReturn("MUTUAL");
        when(userFollowService.isFollowing(2L, 1L)).thenReturn(true);
        when(userFollowService.isFollowing(1L, 2L)).thenReturn(true);

        PrivacyVisibility result = privacyService.checkVisibility(2L, 1L);

        assertThat(result.birthdayVisible()).isTrue();
        assertThat(result.emailVisible()).isTrue();
    }

    @Test
    @DisplayName("陌生人查看，设置为互关可见 -> 生日邮箱隐藏")
    void stranger_MutualSetting_Hidden() {
        when(userSettingService.getSetting(1L, KEY_BIRTHDAY, "ALL")).thenReturn("MUTUAL");
        when(userSettingService.getSetting(1L, KEY_EMAIL, "ALL")).thenReturn("MUTUAL");
        when(userFollowService.isFollowing(2L, 1L)).thenReturn(false);

        PrivacyVisibility result = privacyService.checkVisibility(2L, 1L);

        assertThat(result.birthdayVisible()).isFalse();
        assertThat(result.emailVisible()).isFalse();
    }

    @Test
    @DisplayName("陌生人查看，粉丝/关注/收藏/帖子设为仅自己 -> 均隐藏")
    void stranger_SelfSetting_ListFieldsHidden() {
        when(userSettingService.getSetting(1L, KEY_FOLLOWERS, "ALL")).thenReturn("SELF");
        when(userSettingService.getSetting(1L, KEY_FOLLOWING, "ALL")).thenReturn("SELF");
        when(userSettingService.getSetting(1L, KEY_COLLECTIONS, "ALL")).thenReturn("SELF");
        when(userSettingService.getSetting(1L, KEY_POSTS, "ALL")).thenReturn("SELF");
        when(userFollowService.isFollowing(any(), any())).thenReturn(false);

        PrivacyVisibility result = privacyService.checkVisibility(2L, 1L);

        assertThat(result.followersVisible()).isFalse();
        assertThat(result.followingVisible()).isFalse();
        assertThat(result.collectionsVisible()).isFalse();
        assertThat(result.postsVisible()).isFalse();
    }

    @Test
    @DisplayName("互关好友查看，粉丝/关注/收藏/帖子设为互关可见 -> 均可见")
    void mutual_MutualSetting_ListFieldsVisible() {
        when(userSettingService.getSetting(1L, KEY_FOLLOWERS, "ALL")).thenReturn("MUTUAL");
        when(userSettingService.getSetting(1L, KEY_FOLLOWING, "ALL")).thenReturn("MUTUAL");
        when(userSettingService.getSetting(1L, KEY_COLLECTIONS, "ALL")).thenReturn("MUTUAL");
        when(userSettingService.getSetting(1L, KEY_POSTS, "ALL")).thenReturn("MUTUAL");
        when(userFollowService.isFollowing(2L, 1L)).thenReturn(true);
        when(userFollowService.isFollowing(1L, 2L)).thenReturn(true);

        PrivacyVisibility result = privacyService.checkVisibility(2L, 1L);

        assertThat(result.followersVisible()).isTrue();
        assertThat(result.followingVisible()).isTrue();
        assertThat(result.collectionsVisible()).isTrue();
        assertThat(result.postsVisible()).isTrue();
    }
}