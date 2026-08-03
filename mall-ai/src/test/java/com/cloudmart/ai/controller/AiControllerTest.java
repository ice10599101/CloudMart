package com.cloudmart.ai.controller;

import com.cloudmart.ai.converter.AiConverter;
import com.cloudmart.ai.dto.AiSearchResponse;
import com.cloudmart.ai.dto.ChatResponse;
import com.cloudmart.ai.dto.ProductSearchResult;
import com.cloudmart.ai.dto.VectorSearchResult;
import com.cloudmart.ai.service.AiChatService;
import com.cloudmart.ai.service.AiSearchService;
import com.cloudmart.ai.service.ReviewSummaryService;
import com.cloudmart.ai.service.VectorSearchService;
import com.cloudmart.ai.vo.ChatResponseVO;
import com.cloudmart.ai.vo.ReviewSummaryVO;
import com.cloudmart.ai.vo.SearchResultVO;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiControllerTest {

    private MockMvc mockMvc;

    private final AiChatService chatService = Mockito.mock(AiChatService.class);
    private final AiSearchService searchService = Mockito.mock(AiSearchService.class);
    private final VectorSearchService vectorSearchService = Mockito.mock(VectorSearchService.class);
    private final ReviewSummaryService reviewSummaryService = Mockito.mock(ReviewSummaryService.class);
    private final AiConverter aiConverter = Mockito.mock(AiConverter.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AiController(
                        chatService, searchService, vectorSearchService, reviewSummaryService, aiConverter))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("AI智能对话 - 成功返回信封")
    void chat_ShouldReturnEnvelope() throws Exception {
        ChatResponse dto = new ChatResponse("推荐商品A", "conv-123", false);
        ChatResponseVO vo = new ChatResponseVO("conv-123", "推荐商品A", null);

        given(chatService.chat(eq(1L), Mockito.any())).willReturn(dto);
        given(aiConverter.chatResponseToVO(dto)).willReturn(vo);

        mockMvc.perform(post("/chat")
                        .header("X-User-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"推荐商品\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sessionId").value("conv-123"))
                .andExpect(jsonPath("$.data.reply").value("推荐商品A"));
    }

    @Test
    @DisplayName("AI智能对话 - 缺少消息内容返回校验错误")
    void chat_WhenMissingMessage_ShouldReturnValidationError() throws Exception {
        mockMvc.perform(post("/chat")
                        .header("X-User-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("AI语义搜索 - 成功返回信封")
    void search_ShouldReturnEnvelope() throws Exception {
        ProductSearchResult product = new ProductSearchResult(
                1L, "商品A", "描述", BigDecimal.valueOf(99.00), "img.jpg", "电子产品", 0.95);
        AiSearchResponse searchResponse = new AiSearchResponse(List.of(product), "推荐说明", false);
        SearchResultVO vo = new SearchResultVO(1L, "商品A", BigDecimal.valueOf(99.00), "img.jpg", 0.95);

        given(searchService.search(1L, "手机")).willReturn(searchResponse);
        given(aiConverter.aiSearchResponseToSearchResultVOList(searchResponse)).willReturn(List.of(vo));

        mockMvc.perform(get("/search")
                        .header("X-User-Id", 1)
                        .param("query", "手机"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].productId").value(1))
                .andExpect(jsonPath("$.data[0].name").value("商品A"));
    }

    @Test
    @DisplayName("向量语义搜索 - 成功返回信封")
    void vectorSearch_ShouldReturnEnvelope() throws Exception {
        VectorSearchResult result = new VectorSearchResult(
                1L, "商品A", "描述", BigDecimal.valueOf(99.00), "img.jpg", "电子产品", 0.92);

        given(vectorSearchService.semanticSearch("手机", 10)).willReturn(List.of(result));

        mockMvc.perform(get("/vector-search")
                        .header("X-User-Id", 1)
                        .param("query", "手机"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].similarityScore").value(0.92));
    }

    @Test
    @DisplayName("混合搜索 - 成功返回信封")
    void hybridSearch_ShouldReturnEnvelope() throws Exception {
        VectorSearchResult result = new VectorSearchResult(
                1L, "商品A", "描述", BigDecimal.valueOf(99.00), "img.jpg", "电子产品", 0.88);

        given(vectorSearchService.hybridSearch("手机", 10)).willReturn(List.of(result));

        mockMvc.perform(get("/hybrid-search")
                        .header("X-User-Id", 1)
                        .param("query", "手机"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1));
    }

    @Test
    @DisplayName("评论语义摘要 - 成功返回信封")
    void reviewSummary_ShouldReturnEnvelope() throws Exception {
        ReviewSummaryService.ReviewSummaryResult summaryResult =
                new ReviewSummaryService.ReviewSummaryResult(1L, "质量好", "价格高", "值得购买", 100, false);
        ReviewSummaryVO vo = new ReviewSummaryVO(1L, "优点: 质量好; 缺点: 价格高; 总评: 值得购买", 0.8, 0.2, 100);

        given(reviewSummaryService.summarizeReviews(1L)).willReturn(summaryResult);
        given(aiConverter.reviewSummaryResultToVO(summaryResult)).willReturn(vo);

        mockMvc.perform(get("/reviews/summary/1")
                        .header("X-User-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.productId").value(1))
                .andExpect(jsonPath("$.data.totalReviews").value(100));
    }
}
