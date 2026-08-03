package com.cloudmart.ai.service.impl;

import com.cloudmart.ai.dto.AiSearchResponse;
import com.cloudmart.ai.dto.ProductSearchResult;
import com.cloudmart.ai.dto.VectorSearchResult;
import com.cloudmart.ai.service.VectorSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiSearchServiceImplTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private VectorSearchService vectorSearchService;

    private AiSearchServiceImpl aiSearchService;

    private static final Long USER_ID = 1001L;
    private static final String QUERY = "适合送给新手的露营装备";

    private List<VectorSearchResult> buildVectorResults(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(i -> new VectorSearchResult(
                        (long) i, "商品" + i, "描述" + i, new BigDecimal("99." + i),
                        "http://img.test.com/" + i + ".jpg", "分类" + i, 0.9 - i * 0.01
                ))
                .toList();
    }

    private void setupChatClientMock(String content) {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        doReturn(requestSpec).when(requestSpec).user(any(Consumer.class));
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(content);
    }

    private void setupChatClientFailure() {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        doReturn(requestSpec).when(requestSpec).user(any(Consumer.class));
        when(requestSpec.call()).thenThrow(new RuntimeException("LLM unavailable"));
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

        ReflectionTestUtils.setField(aiSearchService, "restClient", restClient);
    }

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        aiSearchService = new AiSearchServiceImpl(
                chatClientBuilder, vectorSearchService, "http://mall-product", true
        );
    }

    @Nested
    @DisplayName("search - 功能开关")
    class SearchDisabledTests {

        @Test
        @DisplayName("should return empty results when search is disabled")
        void search_disabled_returnsEmptyResults() {
            AiSearchServiceImpl disabledService = new AiSearchServiceImpl(
                    chatClientBuilder, vectorSearchService, "http://mall-product", false
            );

            AiSearchResponse response = disabledService.search(USER_ID, QUERY);

            assertThat(response.products()).isEmpty();
            assertThat(response.explanation()).isEqualTo("AI 搜索功能未启用");
            assertThat(response.degraded()).isTrue();
        }
    }

    @Nested
    @DisplayName("search - 向量检索充足")
    class SearchWithSufficientVectorResultsTests {

        @Test
        @DisplayName("should return vector results directly when >= 5 results found")
        void search_sufficientVectorResults_returnsResultsDirectly() {
            List<VectorSearchResult> vectorResults = buildVectorResults(6);
            when(vectorSearchService.hybridSearch(QUERY, 10)).thenReturn(vectorResults);
            setupChatClientMock("这些露营装备非常适合新手使用");

            AiSearchResponse response = aiSearchService.search(USER_ID, QUERY);

            assertThat(response.products()).hasSize(6);
            assertThat(response.degraded()).isFalse();
        }

        @Test
        @DisplayName("should generate LLM explanation for results")
        void search_sufficientVectorResults_generatesExplanation() {
            when(vectorSearchService.hybridSearch(QUERY, 10)).thenReturn(buildVectorResults(5));
            setupChatClientMock("这些商品性价比高，适合新手");

            AiSearchResponse response = aiSearchService.search(USER_ID, QUERY);

            assertThat(response.explanation()).isEqualTo("这些商品性价比高，适合新手");
        }
    }

    @Nested
    @DisplayName("search - 向量检索不足，关键词补充")
    class SearchWithInsufficientVectorResultsTests {

        @Test
        @DisplayName("should supplement with keyword search when vector results < 5")
        void search_insufficientVectorResults_supplementsWithKeywordSearch() {
            List<VectorSearchResult> vectorResults = buildVectorResults(3);
            when(vectorSearchService.hybridSearch(QUERY, 10)).thenReturn(vectorResults);

            Map<String, Object> keywordData = Map.of(
                    "records", List.of(
                            Map.of("id", 100, "name", "帐篷", "description", "户外帐篷",
                                    "price", "299", "mainImage", "img.jpg", "categoryName", "户外")
                    )
            );
            Map<String, Object> keywordResponse = Map.of("success", true, "data", keywordData);
            setupRestClientMock(keywordResponse);
            setupChatClientMock("为您找到了相关露营装备");

            AiSearchResponse response = aiSearchService.search(USER_ID, QUERY);

            assertThat(response.products()).hasSizeGreaterThanOrEqualTo(3);
        }

        @Test
        @DisplayName("should deduplicate keyword results against vector results")
        void search_insufficientVectorResults_deduplicatesResults() {
            VectorSearchResult existingResult = new VectorSearchResult(
                    100L, "帐篷", "户外帐篷", new BigDecimal("299"),
                    "img.jpg", "户外", 0.9
            );
            when(vectorSearchService.hybridSearch(QUERY, 10)).thenReturn(List.of(existingResult));

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
            setupChatClientMock("推荐这些户外装备");

            AiSearchResponse response = aiSearchService.search(USER_ID, QUERY);

            assertThat(response.products().stream().map(ProductSearchResult::id).distinct().count())
                    .isEqualTo(response.products().size());
        }
    }

    @Nested
    @DisplayName("search - LLM 降级")
    class SearchLlmDegradationTests {

        @Test
        @DisplayName("should degrade gracefully when LLM explanation fails")
        void search_llmFails_degradesGracefully() {
            when(vectorSearchService.hybridSearch(QUERY, 10)).thenReturn(buildVectorResults(5));
            setupChatClientFailure();

            AiSearchResponse response = aiSearchService.search(USER_ID, QUERY);

            assertThat(response.products()).hasSize(5);
            assertThat(response.explanation()).contains("5");
        }

        @Test
        @DisplayName("should return degraded flag when all results are empty")
        void search_allResultsEmpty_returnsDegraded() {
            when(vectorSearchService.hybridSearch(QUERY, 10)).thenReturn(Collections.emptyList());

            Map<String, Object> keywordResponse = Map.of("success", false);
            setupRestClientMock(keywordResponse);

            AiSearchResponse response = aiSearchService.search(USER_ID, QUERY);

            assertThat(response.degraded()).isTrue();
        }

        @Test
        @DisplayName("should handle keyword search RestClient exception")
        void search_keywordSearchException_fallsBackGracefully() {
            when(vectorSearchService.hybridSearch(QUERY, 10)).thenReturn(buildVectorResults(2));

            RestClient restClient = mock(RestClient.class);
            RestClient.RequestHeadersUriSpec<?> uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
            doReturn(uriSpec).when(restClient).get();
            doThrow(new RuntimeException("Connection refused")).when(uriSpec).uri(anyString(), any(Object[].class));
            ReflectionTestUtils.setField(aiSearchService, "restClient", restClient);

            setupChatClientMock("为您找到了部分商品");

            AiSearchResponse response = aiSearchService.search(USER_ID, QUERY);

            assertThat(response.products()).hasSize(2);
        }
    }
}
