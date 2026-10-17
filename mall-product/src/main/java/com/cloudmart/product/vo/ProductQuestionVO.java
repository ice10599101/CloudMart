package com.cloudmart.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品问答条目（PRODUCT-01 两表模型）：问题 + 全部可见答案；
 * 未回答的问题也返回（answers 为空数组），商品详情可见"待回答"问题。
 */
@Schema(description = "商品问答条目")
public record ProductQuestionVO(
        @Schema(description = "问题 ID") Long id,
        @Schema(description = "商品 ID") Long productId,
        @Schema(description = "提问人用户 ID") Long askerUserId,
        @Schema(description = "提问人昵称") String askerNickname,
        @Schema(description = "问题内容") String question,
        @Schema(description = "答案列表（一问多答；可空数组）") List<ProductAnswerVO> answers,
        @Schema(description = "提问时间") LocalDateTime createdAt
) {
}
