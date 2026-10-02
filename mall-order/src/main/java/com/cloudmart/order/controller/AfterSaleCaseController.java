package com.cloudmart.order.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.order.dto.AfterSaleCaseVO;
import com.cloudmart.order.dto.CreateAfterSaleRequest;
import com.cloudmart.order.service.AfterSaleCaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 售后案件用户端（T11）：申请/撤销/详情（归属校验）。
 * 申请不改订单状态（订单仍 PAID/SHIPPED），受理后走 T02 退款链路。
 */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
@Tag(name = "售后案件", description = "T11 售后申请与进度（用户侧）")
public class AfterSaleCaseController {

    private final AfterSaleCaseService afterSaleCaseService;

    @PostMapping("/{orderId}/after-sale")
    @Operation(summary = "申请售后", description = "PAID/SHIPPED 可申请；未发货仅退款，已发货按项退货退款；同单同项 PENDING 唯一")
    public ApiResponse<AfterSaleCaseVO> apply(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "订单ID", required = true) @PathVariable Long orderId,
            @Valid @RequestBody CreateAfterSaleRequest request) {
        // orderId 以路径为准（防 body 越权改单）
        CreateAfterSaleRequest scoped = new CreateAfterSaleRequest(
                orderId, request.itemId(), request.type(), request.reason(),
                request.attachmentFileIds(), request.quantity());
        return ApiResponse.ok(afterSaleCaseService.apply(userId, scoped));
    }

    @PostMapping("/after-sale/{caseId}/cancel")
    @Operation(summary = "撤销售后申请", description = "仅 PENDING 可撤销（归属校验）")
    public ApiResponse<Void> cancel(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "案件ID", required = true) @PathVariable Long caseId) {
        afterSaleCaseService.cancel(userId, caseId);
        return ApiResponse.ok(null);
    }

    public record ReturnShippingRequest(String carrier, String trackingNo) {}

    @PostMapping("/after-sale/{caseId}/return-shipping")
    @Operation(summary = "登记退货运单", description = "T11：RETURN_REFUND 且 APPROVED；承运商+单号唯一")
    public ApiResponse<Void> registerReturnShipping(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "案件ID", required = true) @PathVariable Long caseId,
            @RequestBody ReturnShippingRequest request) {
        if (request.carrier() == null || request.carrier().isBlank()
                || request.trackingNo() == null || request.trackingNo().isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", "承运商与运单号必填");
        }
        afterSaleCaseService.registerReturnShipping(userId, caseId, request.carrier(), request.trackingNo());
        return ApiResponse.ok(null);
    }

    @GetMapping("/after-sale/{caseId}")
    @Operation(summary = "售后案件详情", description = "含时间线（归属校验，只能查本人案件）")
    public ApiResponse<AfterSaleCaseVO> detail(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "案件ID", required = true) @PathVariable Long caseId) {
        return ApiResponse.ok(afterSaleCaseService.detail(userId, caseId));
    }
}
