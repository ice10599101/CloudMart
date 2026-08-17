package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 心愿分类 VO（对应文档 2.1 GET /wish/categories 字典响应）。
 *
 * <p>字典类数据，Redis 缓存（{@code wish:categories}，TTL 1h + 随机抖动）。
 * 管理后台变更分类时主动删除缓存。</p>
 *
 * @param id        分类 ID
 * @param code      分类编码（唯一）
 * @param name      分类名称
 * @param icon      分类图标 URL
 * @param sortOrder 排序值（升序）
 */
@Schema(name = "CategoryVO", description = "心愿分类")
public record CategoryVO(
        @Schema(description = "分类 ID") Long id,
        @Schema(description = "分类编码") String code,
        @Schema(description = "分类名称") String name,
        @Schema(description = "分类图标 URL") String icon,
        @Schema(description = "排序值") Integer sortOrder
) {}
