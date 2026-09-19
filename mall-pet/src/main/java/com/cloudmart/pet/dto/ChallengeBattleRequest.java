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

        @Schema(description = "防守方宠物 ID（PvE 时可空，服务端生成野生宠物）")
        Long defenderPetId
) {
}
