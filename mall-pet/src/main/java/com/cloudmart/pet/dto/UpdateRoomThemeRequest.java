package com.cloudmart.pet.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 更换房间主题请求（三期家园）：墙纸/地板传家具编码，传 null 表示恢复默认。
 * 服务端校验"已拥有 + 分类匹配"（墙纸必须 WALL、地板必须 FLOOR）。
 */
@Schema(description = "更换房间主题请求")
public record UpdateRoomThemeRequest(
        @Schema(description = "墙纸编码（pet_furniture_config.code，null = 默认）") String wallCode,
        @Schema(description = "地板编码（pet_furniture_config.code，null = 默认）") String floorCode
) {
}
