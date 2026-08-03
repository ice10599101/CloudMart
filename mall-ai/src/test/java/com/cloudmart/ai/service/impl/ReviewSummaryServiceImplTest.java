package com.cloudmart.ai.service.impl;

import com.cloudmart.ai.feign.ProductFeignClient;
import com.cloudmart.ai.service.ReviewSummaryService;
import com.cloudmart.common.api.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewSummaryServiceImplTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ProductFeignClient productFeignClient;

    private ReviewSummaryServiceImpl reviewSummaryService;

    private static final Long PRODUCT_ID = 1L;

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
    private ApiResponse<Map<String, Object>> buildReviewsResponse(List<Map<String, Object>> records, long total) {
        Map<String, Object> data = Map.of("records", records, "total", total);
        return ApiResponse.ok(data);
    }

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        reviewSummaryService = new ReviewSummaryServiceImpl(chatClientBuilder, productFeignClient);
    }

    @Nested
    @DisplayName("summarizeReviews - 无评论")
    class NoReviewsTests {

        @Test
        @DisplayName("should return default summary when no reviews exist")
        void summarizeReviews_noReviews_returnsDefaultSummary() {
            ApiResponse<Map<String, Object>> emptyResponse = buildReviewsResponse(Collections.emptyList(), 0);
            when(productFeignClient.getReviews(anyLong(), anyInt(), anyInt(), anyString()))
                    .thenReturn(emptyResponse);

            ReviewSummaryService.ReviewSummaryResult result = reviewSummaryService.summarizeReviews(PRODUCT_ID);

            assertThat(result.productId()).isEqualTo(PRODUCT_ID);
            assertThat(result.pros()).isEqualTo("暂无评价");
            assertThat(result.cons()).isEqualTo("暂无评价");
            assertThat(result.overall()).isEqualTo("该商品暂无用户评价");
            assertThat(result.totalReviews()).isZero();
            assertThat(result.degraded()).isFalse();
        }

        @Test
        @DisplayName("should return default summary when feign client returns null")
        void summarizeReviews_nullResponse_returnsDefaultSummary() {
            when(productFeignClient.getReviews(anyLong(), anyInt(), anyInt(), anyString()))
                    .thenReturn(null);

            ReviewSummaryService.ReviewSummaryResult result = reviewSummaryService.summarizeReviews(PRODUCT_ID);

            assertThat(result.pros()).isEqualTo("暂无评价");
            assertThat(result.totalReviews()).isZero();
        }

        @Test
        @DisplayName("should return default summary when feign client throws exception")
        void summarizeReviews_feignException_returnsDefaultSummary() {
            when(productFeignClient.getReviews(anyLong(), anyInt(), anyInt(), anyString()))
                    .thenThrow(new RuntimeException("Service unavailable"));

            ReviewSummaryService.ReviewSummaryResult result = reviewSummaryService.summarizeReviews(PRODUCT_ID);

            assertThat(result.pros()).isEqualTo("暂无评价");
        }
    }

    @Nested
    @DisplayName("summarizeReviews - LLM 成功")
    class LlmSuccessTests {

        @Test
        @DisplayName("should generate structured summary with LLM")
        void summarizeReviews_llmSuccess_returnsStructuredSummary() {
            Map<String, Object> review1 = Map.of("content", "质量很好", "rating", 5);
            Map<String, Object> review2 = Map.of("content", "价格偏贵", "rating", 3);
            ApiResponse<Map<String, Object>> reviewsResponse = buildReviewsResponse(List.of(review1, review2), 2);

            Map<String, Object> countData = Map.of("records", Collections.emptyList(), "total", 2);
            ApiResponse<Map<String, Object>> countResponse = ApiResponse.ok(countData);

            when(productFeignClient.getReviews(PRODUCT_ID, 0, 50, "mall-ai")).thenReturn(reviewsResponse);
            when(productFeignClient.getReviews(PRODUCT_ID, 0, 1, "mall-ai")).thenReturn(countResponse);

            String llmOutput = """
                    主要优点：
                    1. 质量很好，做工精细
                    2. 使用体验不错
                    
                    主要缺点：
                    1. 价格偏贵
                    
                    总体评价：
                    性价比一般，但品质不错
                    """;
            setupChatClientMock(llmOutput);

            ReviewSummaryService.ReviewSummaryResult result = reviewSummaryService.summarizeReviews(PRODUCT_ID);

            assertThat(result.productId()).isEqualTo(PRODUCT_ID);
            assertThat(result.totalReviews()).isEqualTo(2);
            assertThat(result.degraded()).isFalse();
        }
    }

    @Nested
    @DisplayName("summarizeReviews - LLM 降级")
    class LlmDegradationTests {

        @Test
        @DisplayName("should degrade gracefully when LLM fails")
        void summarizeReviews_llmFails_degradesGracefully() {
            Map<String, Object> review = Map.of("content", "不错", "rating", 4);
            ApiResponse<Map<String, Object>> reviewsResponse = buildReviewsResponse(List.of(review), 5);

            Map<String, Object> countData = Map.of("records", Collections.emptyList(), "total", 5);
            ApiResponse<Map<String, Object>> countResponse = ApiResponse.ok(countData);

            when(productFeignClient.getReviews(PRODUCT_ID, 0, 50, "mall-ai")).thenReturn(reviewsResponse);
            when(productFeignClient.getReviews(PRODUCT_ID, 0, 1, "mall-ai")).thenReturn(countResponse);
            setupChatClientFailure();

            ReviewSummaryService.ReviewSummaryResult result = reviewSummaryService.summarizeReviews(PRODUCT_ID);

            assertThat(result.degraded()).isTrue();
            assertThat(result.totalReviews()).isEqualTo(5);
            assertThat(result.overall()).contains("5");
        }

        @Test
        @DisplayName("should handle LLM returning null content")
        void summarizeReviews_llmNullContent_degradesGracefully() {
            Map<String, Object> review = Map.of("content", "好评", "rating", 5);
            ApiResponse<Map<String, Object>> reviewsResponse = buildReviewsResponse(List.of(review), 1);

            Map<String, Object> countData = Map.of("records", Collections.emptyList(), "total", 1);
            ApiResponse<Map<String, Object>> countResponse = ApiResponse.ok(countData);

            when(productFeignClient.getReviews(PRODUCT_ID, 0, 50, "mall-ai")).thenReturn(reviewsResponse);
            when(productFeignClient.getReviews(PRODUCT_ID, 0, 1, "mall-ai")).thenReturn(countResponse);
            setupChatClientMock(null);

            ReviewSummaryService.ReviewSummaryResult result = reviewSummaryService.summarizeReviews(PRODUCT_ID);

            assertThat(result.totalReviews()).isEqualTo(1);
        }

        @Test
        @DisplayName("should handle count reviews exception gracefully")
        void summarizeReviews_countException_returnsZeroTotal() {
            Map<String, Object> review = Map.of("content", "好评", "rating", 5);
            ApiResponse<Map<String, Object>> reviewsResponse = buildReviewsResponse(List.of(review), 1);

            when(productFeignClient.getReviews(PRODUCT_ID, 0, 50, "mall-ai")).thenReturn(reviewsResponse);
            when(productFeignClient.getReviews(PRODUCT_ID, 0, 1, "mall-ai"))
                    .thenThrow(new RuntimeException("Count failed"));
            setupChatClientMock("这是一个好商品");

            ReviewSummaryService.ReviewSummaryResult result = reviewSummaryService.summarizeReviews(PRODUCT_ID);

            assertThat(result.totalReviews()).isZero();
        }
    }
}
