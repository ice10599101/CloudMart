package com.cloudmart.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 商品问答条目（N-5 问大家）。
 */
@Schema(description = "商品问答条目")
public record ProductQuestionVO(
        @Schema(description = "问题 ID") Long id,
        @Schema(description = "商品 ID") Long productId,
        @Schema(description = "提问人用户 ID") Long askerUserId,
        @Schema(description = "提问人昵称") String askerNickname,
        @Schema(description = "问题内容") String question,
        @Schema(description = "回答内容（可空）") String answer,
        @Schema(description = "回答人用户 ID") Long answerUserId,
        @Schema(description = "回答人昵称") String answererNickname,
        @Schema(description = "回答人是否已购该商品") boolean answerPurchased,
        @Schema(description = "提问时间") LocalDateTime createdAt,
        @Schema(description = "回答时间") LocalDateTime answeredAt
) {
}
