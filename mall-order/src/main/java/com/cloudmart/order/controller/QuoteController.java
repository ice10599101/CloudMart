package com.cloudmart.order.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.order.entity.OrderQuote;
import com.cloudmart.order.service.QuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 服务端报价（TRADE-01 / §8.2）：结算页先报价后下单。
 * 输入只有 skuId + quantity（+可选券），金额/商品信息全部由服务端生成。
 */
@RestController
@RequestMapping("/quotes")
@RequiredArgsConstructor
@Validated
@Tag(name = "订单报价", description = "服务端报价——价格以服务端为准，前端金额不参与记账")
public class QuoteController {

    private final QuoteService quoteService;

    public record CreateQuoteRequest(
            @NotEmpty List<QuoteItem> items,
            Long couponId) {
        public record QuoteItem(@NotNull Long skuId, @NotNull Integer quantity) {
        }
    }

    @PostMapping
    @Operation(summary = "生成报价", description = "SKU 数量 1-999、单次最多 100 行、重复 SKU 合并；"
            + "报价 5 分钟有效；单价/商品信息取服务端权威值")
    public ApiResponse<Map<String, Object>> createQuote(
            @Parameter(description = "用户 ID（已验签令牌主体）", hidden = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody CreateQuoteRequest request) {
        List<QuoteService.QuoteItemInput> items = request.items().stream()
                .map(i -> new QuoteService.QuoteItemInput(i.skuId(), i.quantity()))
                .toList();
        OrderQuote quote = quoteService.createQuote(userId, items, request.couponId());
        return ApiResponse.ok(Map.of(
                "quoteId", String.valueOf(quote.getId()),
                "version", quote.getVersion(),
                "expiresAt", quote.getExpiresAt().toString(),
                "totalAmount", quote.getTotalAmount(),
                "payAmount", quote.getPayAmount()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询报价", description = "仅本人报价可见（他人报价按不存在处理）")
    public ApiResponse<QuoteService.QuoteDetail> getQuote(
            @Parameter(description = "用户 ID（已验签令牌主体）", hidden = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "报价 ID") @PathVariable("id") Long quoteId) {
        return ApiResponse.ok(quoteService.getQuote(userId, quoteId));
    }
}
