package com.cloudmart.order.feign;

import com.cloudmart.common.api.ApiResponse;
import jakarta.validation.constraints.NotNull;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@FeignClient(contextId = "orderPaymentFeignClient", name = "mall-payment", path = "/payments", fallbackFactory = PaymentFeignClientFallbackFactory.class)
public interface PaymentFeignClient {

    @PostMapping
    ApiResponse<PaymentDTO> createPayment(@RequestBody CreatePaymentRequest request);

    @GetMapping("/order/{orderId}")
    ApiResponse<PaymentDTO> getPaymentByOrderId(@PathVariable("orderId") Long orderId);

    @PostMapping("/{paymentId}/refund")
    ApiResponse<PaymentDTO> refund(@PathVariable("paymentId") Long paymentId);

    record CreatePaymentRequest(
            @NotNull Long orderId,
            @NotNull BigDecimal amount,
            String payMethod
    ) {}

    record PaymentDTO(
            Long id, Long orderId, String paymentNo, BigDecimal amount,
            String payMethod, String status, LocalDateTime paidAt,
            LocalDateTime createdAt, String payUrl
    ) {}
}
