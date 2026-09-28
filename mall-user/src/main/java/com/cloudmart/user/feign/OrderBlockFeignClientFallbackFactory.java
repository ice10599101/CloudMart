package com.cloudmart.user.feign;

import com.cloudmart.common.api.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 订单阻塞查询降级（USER-01）：查询失败时返回"有未结订单"语义——
 * 注销宁可被阻塞重试，不能在未核验资金未结的情况下执行擦除（fail-closed）。
 */
@Slf4j
@Component
public class OrderBlockFeignClientFallbackFactory implements FallbackFactory<OrderBlockFeignClient> {

    @Override
    public OrderBlockFeignClient create(Throwable cause) {
        log.error("mall-order 未结订单查询失败: {}", cause.getMessage());
        return (userId, token) -> ApiResponse.ok(true);
    }
}
