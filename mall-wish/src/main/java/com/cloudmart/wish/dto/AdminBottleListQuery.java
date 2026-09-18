package com.cloudmart.wish.dto;

import com.cloudmart.wish.enums.DriftBottleStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 管理后台漂流瓶列表 offset 分页查询参数。
 *
 * <p>状态筛选为物理状态（FLOATING/PICKED/RETURNED）；
 * userId 同时匹配投瓶人与捞瓶人（管理端溯源）。</p>
 */
@Schema(name = "AdminBottleListQuery", description = "管理后台漂流瓶列表 offset 分页查询参数")
public record AdminBottleListQuery(

        @Schema(description = "投瓶人/捞瓶人用户 ID 筛选（可空）")
        Long userId,

        @Schema(description = "物理状态筛选：FLOATING / PICKED / RETURNED（可空）")
        DriftBottleStatus status,

        @Schema(description = "关键词模糊搜索（瓶内文字+关联心愿标题，最多 100 字符）")
        String keyword,

        @Schema(description = "页码（从 1 开始）", defaultValue = "1")
        Integer page,

        @Schema(description = "每页数量（默认 20，最大 100）", defaultValue = "20")
        Integer pageSize
) {
        public AdminBottleListQuery {
                if (page == null || page <= 0) {
                        page = 1;
                }
                if (pageSize == null || pageSize <= 0) {
                        pageSize = 20;
                } else if (pageSize > 100) {
                        pageSize = 100;
                }
        }
}
