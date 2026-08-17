package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 心愿进度 VO（嵌套于 {@link WishVO}）。
 *
 * @param currentValue 当前进度值
 * @param targetValue  目标值
 * @param percentage   完成百分比（0-100，由 currentValue / targetValue * 100 计算）
 * @param version      乐观锁版本号（前端 PUT 进度时必须带回，失配触发 WISH_VERSION_CONFLICT）
 */
@Schema(name = "WishProgressVO", description = "心愿进度")
public record WishProgressVO(
        @Schema(description = "当前进度值") Integer currentValue,
        @Schema(description = "目标值") Integer targetValue,
        @Schema(description = "完成百分比 0-100") Integer percentage,
        @Schema(description = "乐观锁版本号") Integer version
) {}
