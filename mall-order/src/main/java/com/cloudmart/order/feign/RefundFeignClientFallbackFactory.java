package com.cloudmart.order.feign;

import com.cloudmart.common.api.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * T02：退款 Feign 降级——拒绝型 fallback（不假装退款成功，QA06）：
 * 支付服务不可用时审批保持 REFUNDING，可重试/人工核查。
 */
@Slf4j
@Component
public class RefundFeignClientFallbackFactory implements FallbackFactory<RefundFeignClient> {

    @Override
    public RefundFeignClient create(Throwable cause) {
        log.error("退款服务调用失败: {}", cause.getMessage());
        return request -> {
            throw new com.cloudmart.common.exception.BusinessException(
                    "REFUND_SERVICE_UNAVAILABLE", "退款服务不可用，请稍后重试");
        };
    }
}
