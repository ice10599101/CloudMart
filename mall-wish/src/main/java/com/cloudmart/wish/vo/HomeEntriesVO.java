package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 心愿宇宙首页入口开关 VO（嵌套于 {@link HomeAggregationVO}）。
 *
 * <p>对应文档 2.18 entries 字段，控制首页模块入口的渐进开放。
 * 未上线模块返回 false，前端隐藏入口。</p>
 *
 * @param wishEntry          许愿入口（常驻 true）
 * @param mapEntry           地图入口（Sprint 3.1 后开启，Sprint 1.1 为 false）
 * @param aiAssistantEntry   AI 助手入口（Sprint 2.5 后开启，Sprint 1.1 为 false）
 */
@Schema(name = "HomeEntriesVO", description = "首页入口开关")
public record HomeEntriesVO(
        @Schema(description = "许愿入口") Boolean wishEntry,
        @Schema(description = "地图入口") Boolean mapEntry,
        @Schema(description = "AI 助手入口") Boolean aiAssistantEntry
) {}
