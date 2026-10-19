package com.cloudmart.product.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.api.ApiResponse.Meta;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.product.service.ProductQuestionService;
import com.cloudmart.product.vo.ProductQuestionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品问答 - 我的提问（T13）。
 *
 * <p>业务事实是「跨商品的本人提问」，不依赖具体商品。此前挂在
 * {@code /products/{productId}/questions/my} 下导致 Web 端
 * {@code /products/questions/my}（无 productId）调用 404。拆为独立入口，
 * 与商品详情内的 {@code /products/{productId}/questions} 并存。</p>
 */
@RestController
@RequestMapping("/products/questions")
@RequiredArgsConstructor
@Tag(name = "商品问答-我的提问", description = "个人中心跨商品本人提问")
public class MyProductQuestionController {

    private final ProductQuestionService questionService;

    /** T13：删除自己的提问（仅本人；软删全端不可见；重复删除幂等） */
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/my/{questionId}")
    @Operation(summary = "删除自己的提问",
            description = "仅本人（归属不匹配与不存在同语义 404）；软删后商品详情与我的提问均不可见")
    public ApiResponse<Boolean> deleteOwn(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("questionId") Long questionId) {
        questionService.deleteOwn(userId, questionId);
        return ApiResponse.ok(true);
    }

    @GetMapping("/my")
    @Operation(summary = "我的提问（跨商品）", description = "本人提问的问题（含回答进度）；不含 productId")
    public ApiResponse<List<ProductQuestionVO>> myQuestions(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int size) {
        var result = questionService.myQuestions(userId, page, Math.min(size, 50));
        return ApiResponse.ok(result.getRecords(), new Meta(page, Math.min(size, 50), result.getTotal()));
    }
}
