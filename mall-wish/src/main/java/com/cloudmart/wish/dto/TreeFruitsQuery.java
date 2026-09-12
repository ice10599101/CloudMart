package com.cloudmart.wish.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 世界树果实查询参数（对应文档 2.5 GET /wish/tree/fruits）。
 *
 * <p>果实已改为容量封顶单页全量（最多 48 颗，黄金角螺旋布点，
 * 新果实取代旧果实），cursor/bounds/pageSize 参数保留以兼容前端协议，
 * 服务端不再消费。</p>
 */
@Schema(name = "TreeFruitsQuery", description = "世界树果实查询参数（兼容保留，服务端返回全量封顶列表）")
public record TreeFruitsQuery(

        @Schema(description = "兼容保留，服务端不消费")
        String cursor,

        @Schema(description = "兼容保留，服务端不消费")
        Double minLat,

        @Schema(description = "兼容保留，服务端不消费")
        Double maxLat,

        @Schema(description = "兼容保留，服务端不消费")
        Double minLng,

        @Schema(description = "兼容保留，服务端不消费")
        Double maxLng,

        @Schema(description = "兼容保留，服务端不消费", defaultValue = "50")
        Integer pageSize
) {
        public TreeFruitsQuery {
                if (pageSize == null || pageSize <= 0) {
                        pageSize = 50;
                } else if (pageSize > 100) {
                        pageSize = 100;
                }
        }
}
