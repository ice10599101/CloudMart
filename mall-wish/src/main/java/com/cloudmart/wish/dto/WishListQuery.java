package com.cloudmart.wish.dto;

import com.cloudmart.wish.enums.WishStatus;
import com.cloudmart.wish.enums.WishVisibility;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 心愿列表 cursor 分页查询参数（对应文档 2.1 GET /wish/wishes）。
 *
 * <p>游标语义：按 {@code created_at DESC, id DESC} 排序，游标为上一页最后一条记录的 {@code id}。
 * 首页请求不传 cursor；下一页请求传上一页响应 meta.nextCursor。</p>
 *
 * <p>可见性过滤：用户端查询强制 {@code visibility=PUBLIC}（Service 层覆盖入参），
 * 防止越权拉取 PRIVATE/TREE_HOLE 心愿。</p>
 */
@Schema(name = "WishListQuery", description = "心愿列表 cursor 分页查询参数")
public record WishListQuery(

        @Schema(description = "分类 ID 筛选（可空）")
        Long categoryId,

        @Schema(description = "心愿状态筛选（默认 ACTIVE）")
        WishStatus status,

        @Schema(description = "关键词模糊搜索（标题+描述，最多 100 字符）")
        String keyword,

        @Schema(description = "可见性筛选（用户端固定为 PUBLIC，由 Service 层强制）")
        WishVisibility visibility,

        @Schema(description = "分页游标（首页不传，下一页传上一页响应的 nextCursor）")
        String cursor,

        @Schema(description = "每页数量（默认 20，最大 50）", defaultValue = "20")
        Integer pageSize
) {
        public WishListQuery {
                if (pageSize == null || pageSize <= 0) {
                        pageSize = 20;
                } else if (pageSize > 50) {
                        pageSize = 50;
                }
        }
}
