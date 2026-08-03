package com.cloudmart.ai.service.impl;

import com.cloudmart.ai.dto.VectorSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VectorSearchServiceImplTest {

    @Mock
    private VectorStore vectorStore;

    private VectorSearchServiceImpl vectorSearchService;

    private static final String QUERY = "露营装备";
    private static final int TOP_K = 10;

    private List<Document> buildDocuments(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(i -> new Document(
                        "product-" + i,
                        "商品" + i + "描述",
                        Map.of(
                                "productId", i,
                                "name", "商品" + i,
                                "price", BigDecimal.TEN.toString(),
                                "mainImage", "http://img.test.com/" + i + ".jpg",
                                "categoryName", "户外"
                        )
                ))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private void setupRestClientMock(Map<String, Object> responseBody) {
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec<?> uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec<?> headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        doReturn(uriSpec).when(restClient).get();
        doReturn(headersSpec).when(uriSpec).uri(anyString(), any(Object[].class));
        doReturn(headersSpec).when(headersSpec).header(anyString(), any(String[].class));
        doReturn(responseSpec).when(headersSpec).retrieve();
        doReturn(responseBody).when(responseSpec).body(any(Class.class));

        ReflectionTestUtils.setField(vectorSearchService, "productRestClient", restClient);
    }

    @BeforeEach
    void setUp() {
        vectorSearchService = new VectorSearchServiceImpl(vectorStore, true, "http://mall-product");
    }

    @Nested
    @DisplayName("semanticSearch")
    class SemanticSearchTests {

        @Test
        @DisplayName("should return empty results when vector search is disabled")
        void semanticSearch_disabled_returnsEmpty() {
            VectorSearchServiceImpl disabledService = new VectorSearchServiceImpl(vectorStore, false, "http://mall-product");

            List<VectorSearchResult> results = disabledService.semanticSearch(QUERY, TOP_K);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("should return mapped results from vector store")
        void semanticSearch_success_returnsMappedResults() {
            List<Document> documents = buildDocuments(3);
            when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(documents);

            List<VectorSearchResult> results = vectorSearchService.semanticSearch(QUERY, TOP_K);

            assertThat(results).hasSize(3);
            assertThat(results.getFirst().id()).isEqualTo(1L);
            assertThat(results.getFirst().name()).isEqualTo("商品1");
        }

        @Test
        @DisplayName("should return empty results when vector store throws exception")
        void semanticSearch_exception_returnsEmpty() {
            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenThrow(new RuntimeException("ES connection failed"));

            List<VectorSearchResult> results = vectorSearchService.semanticSearch(QUERY, TOP_K);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("should handle documents with missing metadata gracefully")
        void semanticSearch_missingMetadata_returnsResults() {
            Document doc = new Document("product-1", "测试商品", Map.of());
            when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

            List<VectorSearchResult> results = vectorSearchService.semanticSearch(QUERY, TOP_K);

            assertThat(results).hasSize(1);
            assertThat(results.getFirst().name()).isEmpty();
            assertThat(results.getFirst().price()).isNull();
        }
    }

    @Nested
    @DisplayName("hybridSearch")
    class HybridSearchTests {

        @Test
        @DisplayName("should return vector results directly when count >= topK")
        void hybridSearch_sufficientVectorResults_returnsVectorResults() {
            List<Document> documents = buildDocuments(TOP_K);
            when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(documents);

            List<VectorSearchResult> results = vectorSearchService.hybridSearch(QUERY, TOP_K);

            assertThat(results).hasSize(TOP_K);
        }

        @Test
        @DisplayName("should supplement with keyword search when vector results < topK")
        void hybridSearch_insufficientVectorResults_supplementsWithKeywordSearch() {
            List<Document> documents = buildDocuments(3);
            when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(documents);

            Map<String, Object> keywordData = Map.of(
                    "records", List.of(
                            Map.of("id", 100, "name", "帐篷", "description", "户外帐篷",
                                    "price", "299", "mainImage", "img.jpg", "categoryName", "户外")
                    )
            );
            Map<String, Object> keywordResponse = Map.of("success", true, "data", keywordData);
            setupRestClientMock(keywordResponse);

            List<VectorSearchResult> results = vectorSearchService.hybridSearch(QUERY, TOP_K);

            assertThat(results).hasSize(4);
        }

        @Test
        @DisplayName("should deduplicate keyword results against vector results")
        void hybridSearch_deduplicatesResults() {
            Document doc = new Document("product-100", "帐篷", Map.of("productId", 100, "name", "帐篷", "price", "299", "mainImage", "img.jpg", "categoryName", "户外"));
            when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

            Map<String, Object> keywordData = Map.of(
                    "records", List.of(
                            Map.of("id", 100, "name", "帐篷", "description", "户外帐篷",
                                    "price", "299", "mainImage", "img.jpg", "categoryName", "户外"),
                            Map.of("id", 200, "name", "睡袋", "description", "保暖睡袋",
                                    "price", "199", "mainImage", "img2.jpg", "categoryName", "户外")
                    )
            );
            Map<String, Object> keywordResponse = Map.of("success", true, "data", keywordData);
            setupRestClientMock(keywordResponse);

            List<VectorSearchResult> results = vectorSearchService.hybridSearch(QUERY, TOP_K);

            assertThat(results.stream().map(VectorSearchResult::id).filter(id -> id != null && id.equals(100L))).hasSize(1);
            assertThat(results).anySatisfy(r -> assertThat(r.id()).isEqualTo(200L));
        }

        @Test
        @DisplayName("should handle keyword search failure gracefully")
        void hybridSearch_keywordSearchFails_returnsVectorResults() {
            List<Document> documents = buildDocuments(3);
            when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(documents);

            RestClient restClient = mock(RestClient.class);
            RestClient.RequestHeadersUriSpec<?> uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
            doReturn(uriSpec).when(restClient).get();
            doThrow(new RuntimeException("Connection refused")).when(uriSpec).uri(anyString(), any(Object[].class));
            ReflectionTestUtils.setField(vectorSearchService, "productRestClient", restClient);

            List<VectorSearchResult> results = vectorSearchService.hybridSearch(QUERY, TOP_K);

            assertThat(results).hasSize(3);
        }

        @Test
        @DisplayName("should handle keyword search returning failed response")
        void hybridSearch_keywordSearchFailedResponse_returnsVectorResults() {
            List<Document> documents = buildDocuments(3);
            when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(documents);

            Map<String, Object> keywordResponse = Map.of("success", false);
            setupRestClientMock(keywordResponse);

            List<VectorSearchResult> results = vectorSearchService.hybridSearch(QUERY, TOP_K);

            assertThat(results).hasSize(3);
        }
    }
}
