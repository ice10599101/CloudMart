package com.cloudmart.wish.service.impl;

import com.cloudmart.wish.entity.WishFence;

import java.time.LocalDateTime;

/**
 * 围栏判定纯函数（Sprint 3.2，文档 3.2 验收：边界/超范围/有效期）。
 *
 * <p>判定算法：Haversine 距离 ≤ radius（含等号——验收：距离 = radius →
 * true）；有效期含端点；is_active=0 → 恒不命中。</p>
 */
import com.cloudmart.wish.util.GeoHashUtils;

public final class FenceJudge {

    private FenceJudge() {
    }

    /**
     * 空间判定：用户坐标是否在围栏内（距离 ≤ radius，含等号）。
     */
    public static boolean isInside(double fenceLat, double fenceLng, double radiusM,
                                   double userLat, double userLng) {
        return GeoHashUtils.distanceMeters(fenceLat, fenceLng, userLat, userLng) <= radiusM;
    }

    /**
     * 状态与有效期判定：is_active=1 且 now ∈ [valid_from, valid_to]
     *（NULL 表示不限；含端点）。
     */
    public static boolean isEffective(WishFence fence, LocalDateTime nowUtc) {
        if (fence == null || !Boolean.TRUE.equals(fence.getIsActive())) {
            return false;
        }
        if (fence.getValidFrom() != null && nowUtc.isBefore(fence.getValidFrom())) {
            return false;
        }
        return fence.getValidTo() == null || !nowUtc.isAfter(fence.getValidTo());
    }
}
