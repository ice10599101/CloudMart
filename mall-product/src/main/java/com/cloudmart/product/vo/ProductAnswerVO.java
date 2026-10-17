package com.cloudmart.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 商品问答答案条目（PRODUCT-01 一问多答）。
 */
@Schema(description = "商品问答答案")
public record ProductAnswerVO(
        @Schema(description = "答案 ID") Long id,
        @Schema(description = "回答人用户 ID") Long authorUserId,
        @Schema(description = "回答人昵称") String authorNickname,
        @Schema(description = "回答内容") String body,
        @Schema(description = "回答人已购快照（历史资格展示，不作为授权依据；订单服务不可用时为 false/未知）")
        boolean purchased,
        @Schema(description = "回答时间") LocalDateTime createdAt
) {
}
