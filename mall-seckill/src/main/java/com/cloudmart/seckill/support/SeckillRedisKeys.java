package com.cloudmart.seckill.support;

/**
 * 秒杀 Redis 投影键（T09）：库存计数与用户集合是 DB 事实的预筛投影，
 * 键结构集中定义供执行/结果消费/恢复任务共用；投影可随时由请求事实重建。
 */
public final class SeckillRedisKeys {

    private SeckillRedisKeys() {
    }

    public static final String STOCK_KEY_PREFIX = "seckill:stock:";
    public static final String USER_SET_KEY_PREFIX = "seckill:users:";

    public static String stockKey(Long activityId, Long productId) {
        return STOCK_KEY_PREFIX + activityId + ":" + productId;
    }

    public static String userSetKey(Long activityId, Long productId) {
        return USER_SET_KEY_PREFIX + activityId + ":" + productId;
    }
}
