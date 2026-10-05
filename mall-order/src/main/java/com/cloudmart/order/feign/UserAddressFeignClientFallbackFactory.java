package com.cloudmart.order.feign;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.feign.FeignBusinessErrors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 地址查询降级（T09/T10）：fail-closed——查不到地址不能建无收货人订单
 * （orders.receiver_* NOT NULL），抛错走消费者失败分类。
 */
@Slf4j
@Component
public class UserAddressFeignClientFallbackFactory implements FallbackFactory<UserAddressFeignClient> {

    @Override
    public UserAddressFeignClient create(Throwable cause) {
        log.warn("[T09/T10] 用户默认地址查询不可用: {}", cause.getMessage());
        return userId -> {
            throw FeignBusinessErrors.parse(cause, "ADDRESS_SERVICE_UNAVAILABLE", "收货地址服务暂不可用，请稍后重试");
        };
    }
}
