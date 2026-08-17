package com.cloudmart.wish.dto;

import com.cloudmart.wish.enums.AuditStatus;
import com.cloudmart.wish.enums.WishStatus;
import com.cloudmart.wish.enums.WishVisibility;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 管理后台心愿列表 offset 分页查询参数。
 *
 * <p>对应文档 2.0 节"管理后台列表保留 offset 分页（需跳页）"。
 * 支持多维度筛选，供管理后台审核/统计场景使用。</p>
 */
@Schema(name = "AdminWishListQuery", description = "管理后台心愿列表 offset 分页查询参数")
public record AdminWishListQuery(

        @Schema(description = "作者用户 ID 筛选（可空）")
        Long userId,

        @Schema(description = "分类 ID 筛选（可空）")
        Long categoryId,

        @Schema(description = "心愿状态筛选（可空）")
        WishStatus status,

        @Schema(description = "审核状态筛选（可空，常用于 PENDING 待审核列表）")
        AuditStatus auditStatus,

        @Schema(description = "可见性筛选（可空）")
        WishVisibility visibility,

        @Schema(description = "关键词模糊搜索（标题+描述+作者昵称，最多 100 字符）")
        String keyword,

        @Schema(description = "页码（从 1 开始）", defaultValue = "1")
        Integer page,

        @Schema(description = "每页数量（默认 20，最大 100）", defaultValue = "20")
        Integer pageSize
) {
        public AdminWishListQuery {
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
