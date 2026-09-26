package com.cloudmart.pet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 发起对战挑战请求。PvE 由服务端匹配野生宠物（defenderPetId 可空）；
 * PvP 必须携带防守方宠物 ID。
 */
@Schema(description = "对战挑战请求")
public record ChallengeBattleRequest(
        @Schema(description = "对战模式: PVE/PVP", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "请选择对战模式")
        String mode,

        @Schema(description = "防守方宠物 ID（PvP 必填）")
        Long defenderPetId,

        @Schema(description = "野生对手模板 ID（PvE：来自 /battle/opponents 的 templateId，1-3；"
                + "缺省取 1 兼容旧客户端）。选哪只就挑战哪只，服务端不再随机")
        Integer templateId
) {
}
