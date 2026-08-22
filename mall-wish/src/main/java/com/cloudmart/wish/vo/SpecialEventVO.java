package com.cloudmart.wish.vo;

import com.cloudmart.wish.enums.SpecialEventStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 全站特殊事件 VO（Sprint 2.2，四端环境渲染 + 管理端事件列表共用）。
 *
 * @param id          事件 ID
 * @param eventCode   事件代码（关联 wish_env_config.env_code，前端按 code 取渲染配置）
 * @param title       事件标题
 * @param description 事件描述
 * @param status      状态（公开接口返回的活跃事件恒为 ACTIVE）
 * @param triggeredAt 触发时间（UTC，全站同步展示起点）
 * @param expiresAt   过期时间（UTC）；null 表示持续至管理员手动结束
 */
@Schema(name = "SpecialEventVO", description = "世界生命树全站特殊事件")
public record SpecialEventVO(

        @Schema(description = "事件 ID", example = "1948372610")
        Long id,

        @Schema(description = "事件代码（前端按此取 wish_env_config 渲染配置）", example = "METEOR_SHOWER")
        String eventCode,

        @Schema(description = "事件标题", example = "流星雨")
        String title,

        @Schema(description = "事件描述")
        String description,

        @Schema(description = "状态（ACTIVE/ENDED）", example = "ACTIVE")
        SpecialEventStatus status,

        @Schema(description = "触发时间（UTC，ISO 8601）")
        LocalDateTime triggeredAt,

        @Schema(description = "过期时间（UTC）；null 表示持续至手动结束")
        LocalDateTime expiresAt
) {
}
