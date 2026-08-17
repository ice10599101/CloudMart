package com.cloudmart.wish.dto;

import com.cloudmart.wish.enums.WishStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 我的心愿列表 cursor 分页查询参数（对应文档 2.6 GET /wish/my/wishes）。
 *
 * <p>仅支持按状态筛选，分类/关键词筛选走公开列表 API。
 * 用户身份由 {@code X-User-Id} 请求头确定，不可由客户端参数覆盖。</p>
 */
@Schema(name = "MyWishListQuery", description = "我的心愿列表 cursor 分页查询参数")
public record MyWishListQuery(

        @Schema(description = "心愿状态筛选（可空，默认返回所有状态）")
        WishStatus status,

        @Schema(description = "分页游标（首页不传，下一页传上一页响应的 nextCursor）")
        String cursor,

        @Schema(description = "每页数量（默认 20，最大 50）", defaultValue = "20")
        Integer pageSize
) {
        public MyWishListQuery {
                if (pageSize == null || pageSize <= 0) {
                        pageSize = 20;
                } else if (pageSize > 50) {
                        pageSize = 50;
                }
        }
}
