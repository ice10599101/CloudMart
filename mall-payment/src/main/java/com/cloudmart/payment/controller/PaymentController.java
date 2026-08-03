package com.cloudmart.payment.controller;

import com.cloudmart.common.api.ApiResponse;
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
    @Operation(summary = "创建支付", description = "为订单创建支付记录，幂等设计")
    public ApiResponse<PaymentVO> createPayment(@Valid @RequestBody CreatePaymentRequest request) {
        PaymentDTO dto = paymentService.createPayment(request);
        return ApiResponse.ok(paymentConverter.dtoToVO(dto));
    }

    @PostMapping("/callback")
    @Operation(summary = "支付回调", description = "模拟支付平台回调通知，无需认证")
    public ApiResponse<PaymentVO> handleCallback(@Valid @RequestBody PaymentCallbackRequest request) {
        PaymentDTO dto = paymentService.handleCallback(request);
        return ApiResponse.ok(paymentConverter.dtoToVO(dto));
    }

    @PostMapping("/{paymentId}/refund")
    @Operation(summary = "退款", description = "对已支付订单发起退款")
    public ApiResponse<PaymentVO> refund(
            @Parameter(description = "支付记录ID") @PathVariable("paymentId") Long paymentId) {
        PaymentDTO dto = paymentService.refund(paymentId);
        return ApiResponse.ok(paymentConverter.dtoToVO(dto));
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "查询支付状态", description = "根据订单ID查询支付记录")
    public ApiResponse<PaymentVO> getPaymentByOrderId(
            @Parameter(description = "订单ID") @PathVariable("orderId") Long orderId) {
        PaymentDTO dto = paymentService.getPaymentByOrderId(orderId);
        return ApiResponse.ok(paymentConverter.dtoToVO(dto));
    }

    @PutMapping("/{paymentId}/simulate-success")
    @Operation(summary = "模拟支付成功", description = "开发环境模拟支付成功，仅用于测试")
    public ApiResponse<PaymentVO> simulatePaymentSuccess(
            @Parameter(description = "支付记录ID") @PathVariable("paymentId") Long paymentId) {
        PaymentDTO dto = paymentService.simulatePaymentSuccess(paymentId);
        return ApiResponse.ok(paymentConverter.dtoToVO(dto));
    }
}
