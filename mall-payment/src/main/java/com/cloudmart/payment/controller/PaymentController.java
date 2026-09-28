package com.cloudmart.payment.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.payment.converter.PaymentConverter;
import com.cloudmart.payment.dto.CreatePaymentRequest;
import com.cloudmart.payment.dto.PaymentCallbackRequest;
import com.cloudmart.payment.dto.PaymentDTO;
import com.cloudmart.payment.service.PaymentService;
import com.cloudmart.payment.vo.PaymentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments")
@Tag(name = "支付管理", description = "支付创建、回调、退款、查询接口")
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentConverter paymentConverter;

    public PaymentController(PaymentService paymentService, PaymentConverter paymentConverter) {
        this.paymentService = paymentService;
        this.paymentConverter = paymentConverter;
    }

    @PostMapping
    @Operation(summary = "创建支付", description = "为订单创建支付记录，幂等设计；用户调用校验订单归属")
    public ApiResponse<PaymentVO> createPayment(@Valid @RequestBody CreatePaymentRequest request) {
        PaymentDTO dto = paymentService.createPayment(request, resolveCallerUserIdOrNull());
        return ApiResponse.ok(paymentConverter.dtoToVO(dto));
    }

    @PostMapping("/callback")
    @Operation(summary = "支付回调", description = "模拟支付平台回调通知，无需认证")
    public ApiResponse<PaymentVO> handleCallback(@Valid @RequestBody PaymentCallbackRequest request) {
        PaymentDTO dto = paymentService.handleCallback(request);
        return ApiResponse.ok(paymentConverter.dtoToVO(dto));
    }

    @PostMapping("/{paymentId}/refund")
    @PreAuthorize("hasRole('INTERNAL')") // SEC-01：退款执行仅限 mall-order 服务令牌
    @Operation(summary = "退款", description = "对已支付订单发起退款")
    public ApiResponse<PaymentVO> refund(
            @Parameter(description = "支付记录ID") @PathVariable("paymentId") Long paymentId) {
        PaymentDTO dto = paymentService.refund(paymentId);
        return ApiResponse.ok(paymentConverter.dtoToVO(dto));
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "查询支付状态", description = "根据订单ID查询支付记录；用户调用校验订单归属")
    public ApiResponse<PaymentVO> getPaymentByOrderId(
            @Parameter(description = "订单ID") @PathVariable("orderId") Long orderId) {
        PaymentDTO dto = paymentService.getPaymentByOrderId(orderId, resolveCallerUserIdOrNull());
        return ApiResponse.ok(paymentConverter.dtoToVO(dto));
    }

    /**
     * SEC-04：解析调用方身份——服务令牌调用（ROLE_INTERNAL，如 mall-order 内部创建支付）
     * 返回 null，归属由调用方保证；用户调用返回令牌主体，服务层据此校验订单归属。
     */
    private Long resolveCallerUserIdOrNull() {
        org.springframework.security.core.Authentication authentication =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException("UNAUTHORIZED", "未登录或登录已过期");
        }
        boolean internal = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_INTERNAL".equals(a.getAuthority()));
        if (internal) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(authentication.getPrincipal()));
        } catch (NumberFormatException e) {
            throw new BusinessException("UNAUTHORIZED", "无法识别的调用方身份");
        }
    }

    @PutMapping("/{paymentId}/simulate-success")
    @PreAuthorize("hasRole('INTERNAL')") // SEC-01：模拟支付仅限服务令牌（PAY-01 将进一步按环境禁用）
    @Operation(summary = "模拟支付成功", description = "开发环境模拟支付成功，仅用于测试")
    public ApiResponse<PaymentVO> simulatePaymentSuccess(
            @Parameter(description = "支付记录ID") @PathVariable("paymentId") Long paymentId) {
        PaymentDTO dto = paymentService.simulatePaymentSuccess(paymentId);
        return ApiResponse.ok(paymentConverter.dtoToVO(dto));
    }
}
