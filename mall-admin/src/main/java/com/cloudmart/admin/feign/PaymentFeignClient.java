package com.cloudmart.admin.feign;

import com.cloudmart.admin.dto.feign.PaymentDTO;
import com.cloudmart.admin.dto.feign.PaymentSearchRequest;
import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(contextId = "paymentFeignClient", name = "mall-payment", path = "/admin/payments", fallbackFactory = PaymentFeignClientFallbackFactory.class)
public interface PaymentFeignClient {

    @GetMapping
    ApiResponse<List<PaymentDTO>> listPayments(@SpringQueryMap PaymentSearchRequest request);

    @GetMapping("/order/{orderId}")
    ApiResponse<PaymentDTO> getPaymentByOrderId(@PathVariable("orderId") Long orderId);

    @PostMapping("/{paymentId}/refund")
    ApiResponse<PaymentDTO> refund(@PathVariable("paymentId") Long paymentId);
}
