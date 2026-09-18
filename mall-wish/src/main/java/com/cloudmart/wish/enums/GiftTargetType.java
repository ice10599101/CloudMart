package com.cloudmart.wish.enums;

import java.util.Arrays;

/**
 * 礼物送礼场景枚举（全站虚拟礼物）。
 *
 * <p>对应 {@code wish_gift_record.target_type}：心愿详情 / 社区帖子 / 直播间。
 * 场景对象 ID 归属校验（解析收礼人）由 GiftService 按类型分别处理。</p>
 */
public enum GiftTargetType {

    /** 心愿详情页送礼（接收人为心愿作者） */
    WISH,
    /** 社区帖子送礼（接收人为帖子作者） */
    POST,
    /** 直播间送礼（接收人为主播，广播礼物特效） */
    LIVE_ROOM;

    /**
     * 解析请求中的场景字符串（大小写不敏感）。
     *
     * @throws IllegalArgumentException 非法值（由调用方转为 WISH_VALIDATION_ERROR）
     */
    public static GiftTargetType parse(String value) {
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知的送礼场景: " + value));
    }
}
