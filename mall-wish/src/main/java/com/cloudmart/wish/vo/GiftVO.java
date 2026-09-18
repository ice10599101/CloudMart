package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 礼物目录项 VO（用户端礼物选择器 + 管理端目录列表通用）。
 *
 * @param id             礼物 ID
 * @param name           礼物名称
 * @param iconUrl        图标 URL（空时前端展示默认礼物图标）
 * @param animationUrl   动效资源 URL（可空）
 * @param priceStarlight 星光单价
 * @param status         状态：ON_SHELF / OFF_SHELF（用户端仅返回上架）
 * @param sort           排序值
 * @param description    描述（可空）
 */
@Schema(description = "礼物目录项")
public record GiftVO(
        Long id,
        String name,
        String iconUrl,
        String animationUrl,
        Integer priceStarlight,
        String status,
        Integer sort,
        String description
) {
}
