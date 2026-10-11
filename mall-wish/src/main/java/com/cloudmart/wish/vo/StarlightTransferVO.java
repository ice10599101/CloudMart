package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 星光转赠流水条目（§6/R06）：status=SETTLED 表示等额对转完成；
 * remainingToday 仅转赠响应填充（本次转出后当日剩余额度），历史列表为 null。
 */
@Schema(description = "星光转赠流水条目")
public record StarlightTransferVO(
        @Schema(description = "转赠 ID") Long id,
        @Schema(description = "转出用户 ID") Long fromUserId,
        @Schema(description = "转入用户 ID") Long toUserId,
        @Schema(description = "数量") int amount,
        @Schema(description = "附言") String message,
        @Schema(description = "视角：SENT 已发出 / RECEIVED 已收到") String direction,
        @Schema(description = "时间") LocalDateTime createdAt,
        @Schema(description = "状态：SETTLED=等额对转完成") String status,
        @Schema(description = "本次转出后当日剩余额度（仅转赠响应填充）") Integer remainingToday
) {
}
