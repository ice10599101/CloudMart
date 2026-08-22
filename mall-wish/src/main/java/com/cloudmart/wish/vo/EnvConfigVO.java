package com.cloudmart.wish.vo;

import com.cloudmart.wish.enums.EnvCategory;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 环境配置 VO（Sprint 2.2 环境配置表化，公开配置接口 + 管理端 CRUD 共用）。
 *
 * @param id          配置 ID
 * @param envCode     环境代码（唯一）
 * @param category    环境分类（管理端分组展示）
 * @param name        环境名称
 * @param description 环境描述
 * @param priority    渲染优先级（数值大者胜）
 * @param visual      四端渲染视觉参数（透传 JSON，字段由前端契约定义）
 * @param isActive    是否启用
 */
@Schema(name = "EnvConfigVO", description = "世界生命树环境配置（表化，新增环境不改代码）")
public record EnvConfigVO(

        @Schema(description = "配置 ID", example = "1948372611")
        Long id,

        @Schema(description = "环境代码", example = "METEOR_SHOWER")
        String envCode,

        @Schema(description = "环境分类（WEATHER/SEASON/TIME/SPECIAL_EVENT）", example = "SPECIAL_EVENT")
        EnvCategory category,

        @Schema(description = "环境名称", example = "流星雨")
        String name,

        @Schema(description = "环境描述")
        String description,

        @Schema(description = "渲染优先级（数值大者胜：特殊事件100/情绪80/天气50/季节30/时段10）", example = "100")
        Integer priority,

        @Schema(description = "四端渲染视觉参数（crownColor/skyColor/particle/lightCoreColor 等）")
        JsonNode visual,

        @Schema(description = "是否启用", example = "true")
        boolean isActive
) {
}
