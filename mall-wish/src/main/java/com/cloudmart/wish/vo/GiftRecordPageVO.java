package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 送礼记录分页 VO（cursor 分页信封，供前端"加载更多"）。
 *
 * @param records    当前页记录（id 倒序）
 * @param nextCursor 下一页游标（本页末条记录 ID；null 表示没有更多）
 * @param hasMore    是否还有下一页
 */
@Schema(description = "送礼记录分页")
public record GiftRecordPageVO(
        List<GiftRecordVO> records,
        String nextCursor,
        Boolean hasMore
) {
}
