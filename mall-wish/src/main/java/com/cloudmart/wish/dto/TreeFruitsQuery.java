package com.cloudmart.wish.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 世界树果实分页查询参数（对应文档 2.5 GET /wish/tree/fruits）。
 *
 * <p>游标语义：按 {@code id DESC} 排序，游标为上一页最后一条记录的 {@code id}
 * （与心愿列表游标协议一致）。首页不传 cursor，下一页传响应 meta.nextCursor。</p>
 *
 * <p>bounds 视口过滤（弧度制球面坐标）：lat 映射 phi 纬度角 [0,π]，
 * lng 映射 theta 经度角 [0,2π)；四参数需同时提供，任一异常（负数/超范围/
 * 部分缺失/纬度下界≥上界）整组忽略退化为全量分页，不报错（文档验收口径）；
 * {@code minLng > maxLng} 表示跨 0/2π 经度环绕窗口。解析规则详见
 * {@code TreeBoundsParser}。</p>
 */
@Schema(name = "TreeFruitsQuery", description = "世界树果实 cursor 分页 + bounds 视口过滤查询参数")
public record TreeFruitsQuery(

        @Schema(description = "分页游标（首页不传，下一页传上一页响应的 nextCursor）")
        String cursor,

        @Schema(description = "视口最小纬度角 phi（弧度，[0,π]，含）", example = "0.5")
        Double minLat,

        @Schema(description = "视口最大纬度角 phi（弧度，[0,π]，含）", example = "1.5")
        Double maxLat,

        @Schema(description = "视口最小经度角 theta（弧度，[0,2π]，含；大于 maxLng 时为环绕窗口）", example = "0.0")
        Double minLng,

        @Schema(description = "视口最大经度角 theta（弧度，[0,2π]，含）", example = "1.57")
        Double maxLng,

        @Schema(description = "每页数量（默认 50，最大 100）", defaultValue = "50")
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
