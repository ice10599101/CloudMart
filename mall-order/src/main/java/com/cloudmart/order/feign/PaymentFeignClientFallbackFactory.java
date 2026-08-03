package com.cloudmart.order.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.order.feign.PaymentFeignClient.CreatePaymentRequest;
import com.cloudmart.order.feign.PaymentFeignClient.PaymentDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PaymentFeignClientFallbackFactory implements FallbackFactory<PaymentFeignClient> {

    @Override
    public PaymentFeignClient create(Throwable cause) {
        log.error("支付服务调用失败: {}", cause.getMessage());
        return new PaymentFeignClient() {
            @Override
            public ApiResponse<PaymentDTO> createPayment(CreatePaymentRequest request) {
                throw new com.cloudmart.common.exception.BusinessException(
                        "PAYMENT_SERVICE_UNAVAILABLE", "支付服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<PaymentDTO> getPaymentByOrderId(Long orderId) {
                throw new com.cloudmart.common.exception.BusinessException(
                        "PAYMENT_SERVICE_UNAVAILABLE", "支付服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<PaymentDTO> refund(Long paymentId) {
                throw new com.cloudmart.common.exception.BusinessException(
                        "PAYMENT_SERVICE_UNAVAILABLE", "支付服务不可用，退款失败，请稍后重试");
            }
        };
    }
}
