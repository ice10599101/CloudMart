package com.cloudmart.pet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 摆放家具请求（三期家园）。
 *
 * <p>坐标落在服务端网格内（默认 4×3）：越界 400 {@code PET_ROOM_POS_INVALID}，
 * 格子已被占用 409 {@code PET_ROOM_POS_OCCUPIED}——换位请先卸下再摆。</p>
 */
@Schema(description = "摆放家具请求")
public record PlaceFurnitureRequest(
        @Schema(description = "家具编码", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "请选择家具")
        String furnitureCode,

        @Schema(description = "网格 X（0 起）", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "请选择摆放位置")
        @Min(value = 0, message = "摆放位置非法")
        Integer posX,

        @Schema(description = "网格 Y（0 起）", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "请选择摆放位置")
        @Min(value = 0, message = "摆放位置非法")
        Integer posY
) {
}
