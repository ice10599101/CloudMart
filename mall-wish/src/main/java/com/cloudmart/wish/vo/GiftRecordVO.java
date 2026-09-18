package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 送礼记录 VO（我送出的 / 我收到的 / 场景礼物墙 / 管理端记录列表通用）。
 *
 * <p>senderNickname/receiverNickname 由用户服务批量补全，查询失败时为 null
 * （前端回退展示用户 ID）。</p>
 *
 * @param id              记录 ID
 * @param giftId          礼物 ID
 * @param giftName        礼物名称快照
 * @param giftIconUrl     礼物图标快照（可空）
 * @param count           数量
 * @param totalPrice      总消耗（星光）
 * @param senderId        送礼人用户 ID
 * @param senderNickname  送礼人昵称（补全失败为 null）
 * @param receiverId      收礼人用户 ID
 * @param receiverNickname 收礼人昵称（补全失败为 null）
 * @param targetType      送礼场景
 * @param targetId        场景对象 ID
 * @param message         送礼留言（可空）
 * @param createdAt       送礼时间
 */
@Schema(description = "送礼记录")
public record GiftRecordVO(
        Long id,
        Long giftId,
        String giftName,
        String giftIconUrl,
        Integer count,
        Integer totalPrice,
        Long senderId,
        String senderNickname,
        Long receiverId,
        String receiverNickname,
        String targetType,
        Long targetId,
        String message,
        LocalDateTime createdAt
) {
}
