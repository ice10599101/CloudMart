package com.cloudmart.ai.controller;

import com.cloudmart.ai.converter.AiConverter;
import com.cloudmart.ai.dto.*;
import com.cloudmart.ai.service.AiChatService;
import com.cloudmart.ai.service.AiSearchService;
import com.cloudmart.ai.service.ReviewSummaryService;
import com.cloudmart.ai.service.VectorSearchService;
import com.cloudmart.ai.vo.ChatResponseVO;
import com.cloudmart.ai.vo.ReviewSummaryVO;
import com.cloudmart.ai.vo.SearchResultVO;
import com.cloudmart.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "AI 智能导购", description = "AI 智能导购、语义搜索与评论摘要接口")
@RestController
public class AiController {

    private final AiChatService chatService;
    private final AiSearchService searchService;
    private final VectorSearchService vectorSearchService;
    private final ReviewSummaryService reviewSummaryService;
    private final AiConverter aiConverter;

    public AiController(AiChatService chatService,
                        AiSearchService searchService,
                        VectorSearchService vectorSearchService,
                        ReviewSummaryService reviewSummaryService,
                        AiConverter aiConverter) {
        this.chatService = chatService;
        this.searchService = searchService;
        this.vectorSearchService = vectorSearchService;
        this.reviewSummaryService = reviewSummaryService;
        this.aiConverter = aiConverter;
    }

    @Operation(summary = "AI 智能对话", description = "与 AI 导购助手对话，支持多轮对话和 RAG 增强，LLM 不可用时自动降级")
    @PostMapping("/chat")
    public ApiResponse<ChatResponseVO> chat(
            @Parameter(description = "用户ID") @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody ChatRequest request) {
        ChatResponse dto = chatService.chat(userId, request);
        return ApiResponse.ok(aiConverter.chatResponseToVO(dto));
    }

    @Operation(summary = "AI 语义搜索", description = "基于自然语言的商品搜索，混合向量检索+全文检索，LLM 不可用时降级为关键词搜索")
    @GetMapping("/search")
    public ApiResponse<List<SearchResultVO>> search(
            @Parameter(description = "用户ID") @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "搜索查询") @RequestParam String query) {
        AiSearchResponse response = searchService.search(userId, query);
        return ApiResponse.ok(aiConverter.aiSearchResponseToSearchResultVOList(response));
    }

    @Operation(summary = "向量语义搜索", description = "纯向量相似度检索，将查询文本转为 Embedding 后在 ES 中做 KNN 搜索")
    @GetMapping("/vector-search")
    public ApiResponse<List<VectorSearchResult>> vectorSearch(
            @Parameter(description = "用户ID") @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "搜索查询") @RequestParam String query,
            @Parameter(description = "返回数量") @RequestParam(defaultValue = "10") @Min(1) @Max(50) int topK) {
        return ApiResponse.ok(vectorSearchService.semanticSearch(query, topK));
    }

    @Operation(summary = "混合搜索", description = "同时使用向量相似度 + ES 全文检索，综合排序返回最优结果")
    @GetMapping("/hybrid-search")
    public ApiResponse<List<VectorSearchResult>> hybridSearch(
            @Parameter(description = "用户ID") @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "搜索查询") @RequestParam String query,
            @Parameter(description = "返回数量") @RequestParam(defaultValue = "10") @Min(1) @Max(50) int topK) {
        return ApiResponse.ok(vectorSearchService.hybridSearch(query, topK));
    }

    @Operation(summary = "评论语义摘要", description = "利用 LLM 对商品评论进行智能总结，生成优缺点和总体评价")
    @GetMapping("/reviews/summary/{productId}")
    public ApiResponse<ReviewSummaryVO> reviewSummary(
            @Parameter(description = "用户ID") @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "商品ID") @PathVariable Long productId) {
        ReviewSummaryService.ReviewSummaryResult result = reviewSummaryService.summarizeReviews(productId);
        return ApiResponse.ok(aiConverter.reviewSummaryResultToVO(result));
    }
}
