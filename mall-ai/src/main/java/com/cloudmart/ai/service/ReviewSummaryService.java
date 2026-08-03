package com.cloudmart.ai.service;

/**
 * 评论语义摘要服务：利用 LLM 对商品评论进行智能总结。
 * 将数百条评论浓缩为几条关键观点，帮助用户快速了解商品口碑。
 */
public interface ReviewSummaryService {

    /**
     * 对指定商品的评论生成语义摘要。
     *
     * @param productId 商品ID
     * @return  包含优点、缺点、总体评价的结构化摘要
     */
    ReviewSummaryResult summarizeReviews(Long productId);

    /**
     * 评论摘要结果
     */
    record ReviewSummaryResult(
            Long productId,
            String pros,
            String cons,
            String overall,
            int totalReviews,
            boolean degraded
    ) {}
}
