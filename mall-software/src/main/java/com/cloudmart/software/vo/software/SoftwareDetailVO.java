package com.cloudmart.software.vo.software;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 软件详情（方案 3.3/7.2）：无源 URL 和提取码；游客个性化字段为 null；
 * 分享者资料经用户适配器解析（失败降级 null→前端通用头像，展示场景不阻断详情）。
 */
public record SoftwareDetailVO(
        Long id,
        String name,
        String summary,
        String descriptionHtml,
        Long iconAssetId,
        Long categoryId,
        String categoryName,
        List<SoftwareTagVO> tags,
        List<ScreenshotVO> screenshots,
        /** 软件开发者名称（区别于本站分享者） */
        String developerName,
        String officialUrl,
        String licenseType,
        String attributionType,
        String attributionNote,
        String systemRequirements,
        List<String> languages,
        LocalDateTime publishedAt,
        LocalDateTime lastReleaseAt,
        Integer commentCount,
        Integer favoriteCount,
        Integer ratingCount,
        Double ratingAvg,
        /** 分享者（mall-user 会员；展示降级安全） */
        String ownerNickname,
        String ownerAvatar,
        /** 登录用户个性化：是否已收藏（游客 null） */
        Boolean isFavorited,
        /** 分享者会员 ID（前端判断作者操作如置顶留言；游客也可见） */
        Long ownerId,
        /** 登录用户个性化：我的评分 1~5（未评分/游客 null；P1） */
        Integer myScore,
        /** 登录用户个性化：是否已订阅更新（游客 null；P1） */
        Boolean isSubscribed) {

    /** 截图（无障碍文本随素材走） */
    public record ScreenshotVO(Long assetId, Integer displayOrder, String altText) {
    }
}
