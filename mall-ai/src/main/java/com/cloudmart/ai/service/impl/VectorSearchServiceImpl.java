package com.cloudmart.ai.service.impl;

import com.cloudmart.ai.dto.VectorSearchResult;
import com.cloudmart.ai.service.VectorSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "ai.vector.enabled", havingValue = "true")
public class VectorSearchServiceImpl implements VectorSearchService {

    private static final Logger log = LoggerFactory.getLogger(VectorSearchServiceImpl.class);

    private final VectorStore vectorStore;
    private final RestClient productRestClient;
    private final boolean vectorSearchEnabled;

    public VectorSearchServiceImpl(
            VectorStore vectorStore,
            @Value("${ai.vector.enabled:true}") boolean vectorSearchEnabled,
            @Value("${ai.search.product-service-url:http://mall-product}") String productServiceUrl
    ) {
        this.vectorStore = vectorStore;
        this.vectorSearchEnabled = vectorSearchEnabled;
        this.productRestClient = RestClient.builder()
                .baseUrl(productServiceUrl)
                .build();
    }

    @Override
    public List<VectorSearchResult> semanticSearch(String query, int topK) {
        if (!vectorSearchEnabled) {
            log.warn("Vector search is disabled, returning empty results");
            return Collections.emptyList();
        }

        try {
            List<Document> results = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(topK)
                            .similarityThreshold(0.5)
                            .build()
            );

            return results.stream()
                    .map(this::documentToResult)
                    .toList();
        } catch (Exception e) {
            log.error("Vector search failed, degrading: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<VectorSearchResult> hybridSearch(String query, int topK) {
        // 混合检索：向量检索为主，补充 ES 全文检索
        List<VectorSearchResult> vectorResults = semanticSearch(query, topK);

        // 如果向量检索结果不足，用关键词搜索补充
        if (vectorResults.size() < topK) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> response = productRestClient.get()
                        .uri("/products/search?keyword={keyword}&page=0&size={size}",
                                extractSimpleKeywords(query), topK - vectorResults.size())
                        .header("X-Internal-Call", "mall-ai")
                        .retrieve()
                        .body(Map.class);

                if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                    Map<String, Object> data = (Map<String, Object>) response.get("data");
                    if (data != null) {
                        List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("records");
                        if (records != null) {
                            List<VectorSearchResult> keywordResults = records.stream()
                                    .filter(r -> vectorResults.stream()
                                            .noneMatch(v -> v.id().equals(((Number) r.get("id")).longValue())))
                                    .map(this::mapRecordToResult)
                                    .toList();

                            var combined = new java.util.ArrayList<>(vectorResults);
                            combined.addAll(keywordResults);
                            return combined;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Keyword fallback search failed: {}", e.getMessage());
            }
        }

        return vectorResults;
    }

    private VectorSearchResult documentToResult(Document doc) {
        Map<String, Object> metadata = doc.getMetadata();
        return new VectorSearchResult(
                metadata.get("productId") != null ? ((Number) metadata.get("productId")).longValue() : null,
                (String) metadata.getOrDefault("name", ""),
                doc.getText(),
                metadata.get("price") != null ? new BigDecimal(metadata.get("price").toString()) : null,
                (String) metadata.getOrDefault("mainImage", ""),
                (String) metadata.getOrDefault("categoryName", ""),
                doc.getScore()
        );
    }

    private VectorSearchResult mapRecordToResult(Map<String, Object> record) {
        return new VectorSearchResult(
                record.get("id") != null ? ((Number) record.get("id")).longValue() : null,
                (String) record.get("name"),
                (String) record.get("description"),
                record.get("price") != null ? new BigDecimal(record.get("price").toString()) : null,
                (String) record.get("mainImage"),
                (String) record.get("categoryName"),
                null
        );
    }

    private String extractSimpleKeywords(String query) {
        // 简单提取：去除停用词后取前几个词
        String cleaned = query.replaceAll("[的了是在我你他她它这那有和与或]", " ").trim();
        String[] words = cleaned.split("\\s+");
        return String.join(" ", java.util.Arrays.copyOf(words, Math.min(words.length, 5)));
    }
}
