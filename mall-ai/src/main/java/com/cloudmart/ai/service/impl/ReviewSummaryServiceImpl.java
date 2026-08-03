package com.cloudmart.ai.service.impl;

import com.cloudmart.ai.dto.ReviewSummaryResponse;
import com.cloudmart.ai.feign.ProductFeignClient;
import com.cloudmart.ai.service.ReviewSummaryService;
import com.cloudmart.common.api.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 评论语义摘要服务实现。
 * 从商品服务获取评论数据，通过 LLM 生成优缺点和总体评价的结构化摘要。
 * LLM 不可用时降级为返回简单的统计信息。
 */
@Service
public class ReviewSummaryServiceImpl implements ReviewSummaryService {

    private static final Logger log = LoggerFactory.getLogger(ReviewSummaryServiceImpl.class);

    private static final String SUMMARY_PROMPT = """
        请根据以下商品评论，生成一份简洁的评论摘要。要求：
        1. 列出3-5条主要优点（pros）
        2. 列出2-3条主要缺点（cons）
        3. 给出总体评价（1-2句话）
        4. 用中文回复，语气客观专业
        
        商品评论：
        {reviews}
        """;

    private final ChatClient chatClient;
    private final ProductFeignClient productFeignClient;

    public ReviewSummaryServiceImpl(ChatClient.Builder chatClientBuilder,
                                    ProductFeignClient productFeignClient) {
        this.chatClient = chatClientBuilder.build();
        this.productFeignClient = productFeignClient;
    }

    @Override
    public ReviewSummaryResult summarizeReviews(Long productId) {
        // 获取评论数据
        String reviewsText = fetchReviewsText(productId);

        if (reviewsText.isBlank()) {
            return new ReviewSummaryResult(productId, "暂无评价", "暂无评价", "该商品暂无用户评价", 0, false);
        }

        int totalReviews = countTotalReviews(productId);

        try {
            String summary = chatClient.prompt()
                    .user(userSpec -> userSpec.text(SUMMARY_PROMPT)
                            .param("reviews", reviewsText))
                    .call()
                    .content();

            return parseSummaryResult(productId, summary, totalReviews);
        } catch (Exception e) {
            log.error("LLM review summarization failed for product {}: {}", productId, e.getMessage());
            return new ReviewSummaryResult(
                    productId,
                    "智能摘要暂时不可用",
                    "智能摘要暂时不可用",
                    "该商品共有 " + totalReviews + " 条评价，请查看原始评论了解详情",
                    totalReviews,
                    true
            );
        }
    }

    private String fetchReviewsText(Long productId) {
        try {
            ApiResponse<Map<String, Object>> response = productFeignClient.getReviews(productId, 0, 50, "mall-ai");
            if (response != null && response.success() && response.data() != null) {
                Map<String, Object> data = response.data();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("records");
                if (records != null && !records.isEmpty()) {
                    return records.stream()
                            .map(r -> {
                                String content = (String) r.getOrDefault("content", "");
                                Integer rating = r.get("rating") != null ? ((Number) r.get("rating")).intValue() : null;
                                return "评分:" + (rating != null ? rating + "星" : "未知") + " - " + content;
                            })
                            .collect(Collectors.joining("\n"));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch reviews for product {}: {}", productId, e.getMessage());
        }
        return "";
    }

    private int countTotalReviews(Long productId) {
        try {
            ApiResponse<Map<String, Object>> response = productFeignClient.getReviews(productId, 0, 1, "mall-ai");
            if (response != null && response.success() && response.data() != null) {
                Map<String, Object> data = response.data();
                Object total = data.get("total");
                return total != null ? ((Number) total).intValue() : 0;
            }
        } catch (Exception e) {
            log.warn("Failed to count reviews: {}", e.getMessage());
        }
        return 0;
    }

    private ReviewSummaryResult parseSummaryResult(Long productId, String summary, int totalReviews) {
        // 尝试从 LLM 输出中提取结构化信息
        String pros = extractSection(summary, "优点", "主要优点", "pros");
        String cons = extractSection(summary, "缺点", "主要缺点", "cons");
        String overall = extractSection(summary, "总体", "综合", "overall");

        return new ReviewSummaryResult(
                productId,
                pros.isBlank() ? summary : pros,
                cons,
                overall.isBlank() ? "基于 " + totalReviews + " 条评价生成" : overall,
                totalReviews,
                false
        );
    }

    private String extractSection(String text, String... keywords) {
        String[] lines = text.split("\n");
        StringBuilder section = new StringBuilder();
        boolean capturing = false;

        for (String line : lines) {
            boolean isHeader = false;
            for (String keyword : keywords) {
                if (line.toLowerCase().contains(keyword.toLowerCase())) {
                    isHeader = true;
                    capturing = true;
                    break;
                }
            }
            // 检测新的段落标题（下一个 section 开始）
            if (capturing && !isHeader && line.matches("^\\d+[.、）)].*|^[一二三四五六][、.）].*")) {
                break;
            }
            if (capturing && !isHeader && !line.isBlank()) {
                if (!section.isEmpty()) {
                    section.append("\n");
                }
                section.append(line.trim());
            }
        }

        return section.toString();
    }
}
