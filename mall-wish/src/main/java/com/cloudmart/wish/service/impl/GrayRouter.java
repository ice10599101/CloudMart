package com.cloudmart.wish.service.impl;

/**
 * 灰度路由纯函数（Sprint 2.8，文档 2.8 验收：按用户 ID 哈希分流，
 * 同一用户始终命中同一灰度档）。
 *
 * <p>桶算法：FNV-1a 32 位对 "{userId}:{featureKey}" 稳定哈希 →
 * bucket = hash % 100 → bucket &lt; grayRatio 即命中。
 * 确定性保证同一用户四端命中同一档（无随机、无状态）。</p>
 */
public final class GrayRouter {

    private static final int FNV_OFFSET_BASIS = 0x811c9dc5;
    private static final int FNV_PRIME = 0x01000193;

    private GrayRouter() {
    }

    /** 稳定桶位 0-99（无状态、跨实例一致） */
    public static int bucket(Long userId, String featureKey) {
        String seed = userId + ":" + (featureKey == null ? "" : featureKey);
        int hash = FNV_OFFSET_BASIS;
        for (int i = 0; i < seed.length(); i++) {
            hash ^= seed.charAt(i);
            hash *= FNV_PRIME;
        }
        return Math.floorMod(hash, 100);
    }

    /** 灰度命中判定（userId 为空=匿名，仅全量 ratio>=100 时放行） */
    public static boolean isHit(Long userId, String featureKey, int grayRatio) {
        int ratio = Math.max(0, Math.min(100, grayRatio));
        if (ratio >= 100) {
            return true;
        }
        if (userId == null || ratio <= 0) {
            return false;
        }
        return bucket(userId, featureKey) < ratio;
    }
}
