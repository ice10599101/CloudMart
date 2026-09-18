package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 送礼物结果 VO。
 *
 * @param recordId     送礼记录 ID
 * @param giftId       礼物 ID
 * @param giftName     礼物名称快照
 * @param giftIconUrl  礼物图标快照（可空，前端回退默认图标）
 * @param count        数量
 * @param totalPrice   总消耗（星光）
 * @param balanceAfter 送礼后余额（星光）
 * @param receiverId   收礼人用户 ID
 * @param targetType   送礼场景
 * @param targetId     场景对象 ID
 */
@Schema(description = "送礼物结果")
public record SendGiftResultVO(
        Long recordId,
        Long giftId,
        String giftName,
        String giftIconUrl,
        Integer count,
        Integer totalPrice,
        Integer balanceAfter,
        Long receiverId,
        String targetType,
        Long targetId
) {
}
