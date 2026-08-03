package com.cloudmart.ai.service.impl;

import com.cloudmart.ai.dto.ProductSearchResult;
import com.cloudmart.ai.service.ProductVectorSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "ai.vector.enabled", havingValue = "true")
public class ProductVectorSyncServiceImpl implements ProductVectorSyncService {

    private static final Logger log = LoggerFactory.getLogger(ProductVectorSyncServiceImpl.class);

    private final VectorStore vectorStore;
    private final RestClient productRestClient;

    public ProductVectorSyncServiceImpl(
            VectorStore vectorStore,
            @Value("${ai.search.product-service-url:http://mall-product}") String productServiceUrl
    ) {
        this.vectorStore = vectorStore;
        this.productRestClient = RestClient.builder()
                .baseUrl(productServiceUrl)
                .build();
    }

    @Override
    @Async
    public void fullSync() {
        log.info("Starting full product vector sync...");
        int page = 0;
        int size = 100;
        int totalSynced = 0;

        while (true) {
            List<Map<String, Object>> products = fetchProducts(page, size);
            if (products.isEmpty()) {
                break;
            }

            List<Document> documents = products.stream()
                    .map(this::productToDocument)
                    .toList();

            vectorStore.add(documents);
            totalSynced += documents.size();
            log.info("Synced batch {}: {} products, total: {}", page, documents.size(), totalSynced);

            if (products.size() < size) {
                break;
            }
            page++;
        }

        log.info("Full product vector sync completed: {} products", totalSynced);
    }

    @Override
    public void syncProduct(Long productId) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = productRestClient.get()
                    .uri("/products/{id}", productId)
                    .header("X-Internal-Call", "mall-ai")
                    .retrieve()
                    .body(Map.class);

            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                if (data != null) {
                    Document doc = productToDocument(data);
                    vectorStore.add(List.of(doc));
                    log.info("Synced product {} to vector store", productId);
                }
            }
        } catch (Exception e) {
            log.error("Failed to sync product {}: {}", productId, e.getMessage());
        }
    }

    @Override
    public void deleteProduct(Long productId) {
        try {
            vectorStore.delete(List.of("product-" + productId));
            log.info("Deleted product {} from vector store", productId);
        } catch (Exception e) {
            log.error("Failed to delete product {} from vector store: {}", productId, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchProducts(int page, int size) {
        try {
            Map<String, Object> response = productRestClient.get()
                    .uri("/products?page={page}&size={size}", page, size)
                    .header("X-Internal-Call", "mall-ai")
                    .retrieve()
                    .body(Map.class);

            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                if (data != null) {
                    return (List<Map<String, Object>>) data.getOrDefault("records", Collections.emptyList());
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch products page {}: {}", page, e.getMessage());
        }
        return Collections.emptyList();
    }

    private Document productToDocument(Map<String, Object> product) {
        Long id = product.get("id") != null ? ((Number) product.get("id")).longValue() : 0L;
        String name = (String) product.getOrDefault("name", "");
        String description = (String) product.getOrDefault("description", "");
        BigDecimal price = product.get("price") != null ? new BigDecimal(product.get("price").toString()) : BigDecimal.ZERO;
        String mainImage = (String) product.getOrDefault("mainImage", "");
        String categoryName = (String) product.getOrDefault("categoryName", "");

        // 将商品描述和名称组合为待向量化的文本
        String content = name + "。" + (description != null ? description : "");

        return new Document(
                "product-" + id,
                content,
                Map.of(
                        "productId", id,
                        "name", name,
                        "price", price.toString(),
                        "mainImage", mainImage,
                        "categoryName", categoryName != null ? categoryName : ""
                )
        );
    }
}
