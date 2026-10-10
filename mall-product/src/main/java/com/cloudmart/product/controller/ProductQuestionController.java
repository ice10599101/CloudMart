package com.cloudmart.product.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.product.service.ProductQuestionService;
import com.cloudmart.product.vo.ProductQuestionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品问答（N-5 问大家）：提问需登录；列表公开；回答需登录。
 */
@RestController
@RequestMapping("/products/{productId}/questions")
@RequiredArgsConstructor
@Tag(name = "商品问答", description = "N-5 问大家：购前决策辅助，回答可带已购徽标")
public class ProductQuestionController {

    private final ProductQuestionService questionService;

    public record AskRequest(@NotBlank(message = "问题内容不能为空") String question) {}

    public record AnswerRequest(@NotBlank(message = "回答内容不能为空") String answer) {}

    // N-5：@Operation 置于 @Mapping 之前——route-inventory 扫描器对无参
    // @PostMapping 会取下一注解的首个字符串当路径（把"提问"当子路径），故调换顺序
    @Operation(summary = "提问", description = "任意登录用户可提问；内容转义，长度 1..500")
    @PostMapping
    public ApiResponse<ProductQuestionVO> ask(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable Long productId,
            @RequestBody AskRequest request) {
        return ApiResponse.ok(questionService.ask(userId, productId, request.question()));
    }

    @GetMapping
    @Operation(summary = "问答列表", description = "仅返回已回答的问题（未答问题不出列，防垃圾占位）")
    public ApiResponse<Page<ProductQuestionVO>> list(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(questionService.list(productId, page, Math.min(size, 50)));
    }

    @PostMapping("/{questionId}/answer")
    @Operation(summary = "回答", description = "任意登录用户可回答；回答人已购徽标为回答时快照（订单服务校验）")
    public ApiResponse<ProductQuestionVO> answer(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable Long productId,
            @PathVariable Long questionId,
            @RequestBody AnswerRequest request) {
        return ApiResponse.ok(questionService.answer(userId, questionId, request.answer()));
    }
}
