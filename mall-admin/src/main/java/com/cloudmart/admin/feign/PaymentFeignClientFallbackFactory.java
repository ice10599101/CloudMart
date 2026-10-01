package com.cloudmart.admin.feign;

import com.cloudmart.admin.dto.feign.PaymentAttemptDTO;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class PaymentFeignClientFallbackFactory implements FallbackFactory<PaymentFeignClient> {

    @Override
    public PaymentFeignClient create(Throwable cause) {
        log.error("支付服务调用失败: {}", cause.getMessage());
        return new PaymentFeignClient() {
            @Override
            public ApiResponse<List<PaymentAttemptDTO>> listPayments(String status, Integer page, Integer pageSize) {
                throw new BusinessException("PAYMENT_SERVICE_UNAVAILABLE", "支付服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<PaymentAttemptDTO> getPaymentByOrderId(Long orderId) {
                throw new BusinessException("PAYMENT_SERVICE_UNAVAILABLE", "支付服务不可用，请稍后重试");
            }

        };
    }
}
