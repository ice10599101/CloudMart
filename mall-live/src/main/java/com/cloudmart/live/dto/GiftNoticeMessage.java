package com.cloudmart.live.dto;

/**
 * 直播间礼物特效广播消息（全站虚拟礼物）。
 *
 * <p>经弹幕 WebSocket 通道推送给房间内所有观众；{@code type="GIFT"} 用于
 * 客户端区分礼物特效与普通弹幕（普通弹幕无 type 字段）。</p>
 *
 * @param type           消息类型常量 "GIFT"
 * @param roomId         直播间 ID
 * @param senderId       送礼人用户 ID
 * @param senderNickname 送礼人昵称
 * @param receiverId     主播用户 ID
 * @param giftId         礼物 ID
 * @param giftName       礼物名称快照
 * @param giftIconUrl    礼物图标快照（可空）
 * @param count          数量
 * @param message        送礼留言（可空）
 * @param timestamp      广播时间戳（毫秒）
 */
public record GiftNoticeMessage(
        String type,
        Long roomId,
        Long senderId,
        String senderNickname,
        Long receiverId,
        Long giftId,
        String giftName,
        String giftIconUrl,
        Integer count,
        String message,
        long timestamp
) {

    public static final String TYPE_GIFT = "GIFT";
}
