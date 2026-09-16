package com.cloudmart.community.service.impl;

import com.cloudmart.community.service.PrivacyService;
import com.cloudmart.community.service.UserFollowService;
import com.cloudmart.community.service.UserSettingService;
import com.cloudmart.community.vo.PrivacyVisibility;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 资料字段可见性计算实现。
 *
 * <p>可见性三档等级：ALL=1（所有人）、MUTUAL=2（互关好友）、SELF=3（仅自己）。
 * 查看者关系等级：陌生人=1、互关好友=2、本人=3。
 * 字段可见当且仅当 {@code 查看者等级 >= 字段档位等级}。</p>
 */
@Service
@RequiredArgsConstructor
public class PrivacyServiceImpl implements PrivacyService {

    private static final String KEY_BIRTHDAY = "PRIVACY_BIRTHDAY_VISIBILITY";
    private static final String KEY_EMAIL = "PRIVACY_EMAIL_VISIBILITY";
    private static final String KEY_FOLLOWERS = "PRIVACY_FOLLOWERS_VISIBILITY";
    private static final String KEY_FOLLOWING = "PRIVACY_FOLLOWING_VISIBILITY";
    private static final String KEY_COLLECTIONS = "PRIVACY_COLLECTIONS_VISIBILITY";
    private static final String KEY_POSTS = "PRIVACY_POSTS_VISIBILITY";
    private static final String DEFAULT_VISIBILITY = "ALL";

    private static final int FIELD_LEVEL_ALL = 1;
    private static final int FIELD_LEVEL_MUTUAL = 2;
    private static final int FIELD_LEVEL_SELF = 3;

    private static final int VIEWER_LEVEL_STRANGER = 1;
    private static final int VIEWER_LEVEL_MUTUAL = 2;
    private static final int VIEWER_LEVEL_SELF = 3;

    private final UserSettingService userSettingService;
    private final UserFollowService userFollowService;

    @Override
    public PrivacyVisibility checkVisibility(Long viewerUserId, Long targetUserId) {
        int viewerLevel = resolveViewerLevel(viewerUserId, targetUserId);
        return new PrivacyVisibility(
                isFieldVisible(targetUserId, KEY_BIRTHDAY, viewerLevel),
                isFieldVisible(targetUserId, KEY_EMAIL, viewerLevel),
                isFieldVisible(targetUserId, KEY_FOLLOWERS, viewerLevel),
                isFieldVisible(targetUserId, KEY_FOLLOWING, viewerLevel),
                isFieldVisible(targetUserId, KEY_COLLECTIONS, viewerLevel),
                isFieldVisible(targetUserId, KEY_POSTS, viewerLevel)
        );
    }

    private boolean isFieldVisible(Long targetUserId, String settingKey, int viewerLevel) {
        return isVisible(
                userSettingService.getSetting(targetUserId, settingKey, DEFAULT_VISIBILITY), viewerLevel);
    }

    private int resolveViewerLevel(Long viewerUserId, Long targetUserId) {
        if (viewerUserId == null || targetUserId == null) {
            return VIEWER_LEVEL_STRANGER;
        }
        if (viewerUserId.equals(targetUserId)) {
            return VIEWER_LEVEL_SELF;
        }
        boolean mutual = userFollowService.isFollowing(viewerUserId, targetUserId)
                && userFollowService.isFollowing(targetUserId, viewerUserId);
        return mutual ? VIEWER_LEVEL_MUTUAL : VIEWER_LEVEL_STRANGER;
    }

    private boolean isVisible(String settingValue, int viewerLevel) {
        int fieldLevel = switch (settingValue == null ? "" : settingValue) {
            case "MUTUAL" -> FIELD_LEVEL_MUTUAL;
            case "SELF" -> FIELD_LEVEL_SELF;
            default -> FIELD_LEVEL_ALL;
        };
        return viewerLevel >= fieldLevel;
    }
}