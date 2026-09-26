package com.cloudmart.wish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 送礼物请求（全站虚拟礼物）。
 *
 * @param giftId     礼物 ID（目录中须为上架状态）
 * @param count      数量（1-99）
 * @param targetType 送礼场景：WISH / POST / LIVE_ROOM
 * @param targetId   场景对象 ID（心愿 ID / 帖子 ID / 直播间 ID）
 * @param message    送礼留言（可选，≤100 字符，入库前 XSS 转义由内容审查链路处理）
 */
@Schema(description = "送礼物请求")
public record SendGiftRequest(
        @Schema(description = "礼物 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "礼物不能为空")
        Long giftId,

        @Schema(description = "数量（1-99）", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "数量不能为空")
        @Min(value = 1, message = "数量至少为 1")
        @Max(value = 99, message = "单次最多送 99 个")
        Integer count,

        @Schema(description = "送礼场景", requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"WISH", "POST", "LIVE_ROOM"})
        @NotNull(message = "送礼场景不能为空")
        String targetType,

        @Schema(description = "场景对象 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "场景对象不能为空")
        @Min(value = 1, message = "场景对象 ID 非法")
        Long targetId,

        @Schema(description = "送礼留言（可选，最多 100 字符）")
        @Size(max = 100, message = "留言不能超过 100 字符")
        String message,

        @Schema(description = "幂等键（X-Idempotency-Key；缺省按单次请求处理，B04）")
        String idempotencyKey
) {

    /** 兼容旧调用：缺省幂等键（仅单次请求保护，无跨重试重放）。 */
    public SendGiftRequest(Long giftId, Integer count, String targetType, Long targetId, String message) {
        this(giftId, count, targetType, targetId, message, null);
    }
}
