package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 送礼记录分页 VO（服务层内部载体；控制器解包为「data=数组 + meta 游标」标准信封）。
 *
 * @param records    当前页记录（id 倒序）
 * @param pageSize   归一化后的页大小（写回 meta.pageSize）
 * @param nextCursor 下一页游标（本页末条记录 ID；null 表示没有更多）
 * @param hasMore    是否还有下一页
 */
@Schema(description = "送礼记录分页")
public record GiftRecordPageVO(
        List<GiftRecordVO> records,
        Integer pageSize,
        String nextCursor,
        Boolean hasMore
) {
}
