package com.cloudmart.ai.service.impl;

import com.cloudmart.ai.dto.AiSearchResponse;
import com.cloudmart.ai.dto.ProductSearchResult;
import com.cloudmart.ai.dto.VectorSearchResult;
import com.cloudmart.ai.service.AiSearchService;
import com.cloudmart.ai.service.VectorSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * AI 语义搜索服务实现。
 * 支持混合检索：向量相似度 + ES 全文检索，取并集后由 LLM 生成推荐说明。
 * LLM 不可用时自动降级为纯关键词搜索。
 */
@Service
public class AiSearchServiceImpl implements AiSearchService {

    private static final Logger log = LoggerFactory.getLogger(AiSearchServiceImpl.class);

    private static final String KEYWORD_EXTRACTION_PROMPT = """
        从以下用户查询中提取商品搜索关键词。
        只返回关键词，用空格分隔，不要添加任何解释或额外文字。
        如果用户查询是"适合送给新手的露营装备"，你应该返回"露营 装备 新手"。
        
        用户查询: {query}
        """;

    private final ChatClient chatClient;
    private final VectorSearchService vectorSearchService;
    private final RestClient restClient;
    private final boolean searchEnabled;

    public AiSearchServiceImpl(ChatClient.Builder chatClientBuilder,
                               VectorSearchService vectorSearchService,
                               @Value("${ai.search.product-service-url:http://mall-product}") String productServiceUrl,
                               @Value("${ai.search.enabled:true}") boolean searchEnabled) {
        this.chatClient = chatClientBuilder.build();
        this.vectorSearchService = vectorSearchService;
        this.searchEnabled = searchEnabled;
        this.restClient = RestClient.builder()
                .baseUrl(productServiceUrl)
                .build();
    }

    @Override
    public AiSearchResponse search(Long userId, String query) {
        if (!searchEnabled) {
            return new AiSearchResponse(Collections.emptyList(), "AI 搜索功能未启用", true);
        }

        // 1. 混合检索：向量 + 关键词
        List<VectorSearchResult> vectorResults = vectorSearchService.hybridSearch(query, 10);

        // 2. 转换为统一格式
        List<ProductSearchResult> products = vectorResults.stream()
                .map(this::vectorToSearchResult)
                .toList();

        // 3. 如果向量检索结果不足，用传统搜索补充
        if (products.size() < 5) {
            List<ProductSearchResult> keywordResults = searchProductsByKeyword(query);
            var combined = new java.util.ArrayList<>(products);
            keywordResults.stream()
                    .filter(kr -> combined.stream().noneMatch(p -> p.id().equals(kr.id())))
                    .forEach(combined::add);
            products = combined.stream().limit(10).toList();
        }

        // 4. LLM 生成推荐说明
        String explanation = generateExplanation(query, products);

        boolean degraded = vectorResults.isEmpty() && products.isEmpty();
        return new AiSearchResponse(products, explanation, degraded);
    }

    private ProductSearchResult vectorToSearchResult(VectorSearchResult vr) {
        return new ProductSearchResult(
                vr.id(), vr.name(), vr.description(), vr.price(),
                vr.mainImage(), vr.categoryName(), vr.similarityScore()
        );
    }

    private List<ProductSearchResult> searchProductsByKeyword(String keywords) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.get()
                    .uri("/products/search?keyword={keyword}&page=0&size=10", keywords)
                    .header("X-Internal-Call", "mall-ai")
                    .retrieve()
                    .body(Map.class);

            if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
                return Collections.emptyList();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            if (data == null) return Collections.emptyList();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("records");
            if (records == null) return Collections.emptyList();

            return records.stream().map(this::mapToSearchResult).toList();
        } catch (Exception e) {
            log.error("Keyword search call failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private String generateExplanation(String originalQuery, List<ProductSearchResult> products) {
        if (products.isEmpty()) {
            return "未找到与\"" + originalQuery + "\"相关的商品，请尝试其他关键词。";
        }

        try {
            String productSummary = products.stream()
                    .limit(5)
                    .map(p -> "- " + p.name() + " (¥" + p.price() + ")")
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("");

            String explanation = chatClient.prompt()
                    .user(u -> u.text("""
                            根据用户搜索"{query}"，我们找到了以下商品：
                            {products}
                            
                            请用1-2句话简要说明为什么这些商品符合用户需求，语气友好专业。
                            """)
                            .param("query", originalQuery)
                            .param("products", productSummary))
                    .call()
                    .content();

            return explanation != null ? explanation : "为您找到了 " + products.size() + " 个相关商品";
        } catch (Exception e) {
            log.warn("LLM explanation generation failed: {}", e.getMessage());
            return "为您找到了 " + products.size() + " 个相关商品";
        }
    }

    private ProductSearchResult mapToSearchResult(Map<String, Object> map) {
        return new ProductSearchResult(
                map.get("id") != null ? ((Number) map.get("id")).longValue() : null,
                (String) map.get("name"),
                (String) map.get("description"),
                map.get("price") != null ? new java.math.BigDecimal(map.get("price").toString()) : null,
                (String) map.get("mainImage"),
                (String) map.get("categoryName"),
                map.get("score") != null ? ((Number) map.get("score")).doubleValue() : null
        );
    }
}
