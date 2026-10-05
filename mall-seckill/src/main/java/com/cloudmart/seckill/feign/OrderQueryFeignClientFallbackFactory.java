package com.cloudmart.seckill.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.feign.FeignBusinessErrors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 订单查询降级（T09）：fail-closed——查不到订单状态时不得把排队请求误判为
 * 失败释放占用（可能正有订单在建），抛错让恢复任务下轮再试。
 */
@Slf4j
@Component
public class OrderQueryFeignClientFallbackFactory implements FallbackFactory<OrderQueryFeignClient> {

    @Override
    public OrderQueryFeignClient create(Throwable cause) {
        log.warn("[T09] 订单查询服务暂不可用，恢复对账顺延: {}", cause.getMessage());
        return requestId -> {
            throw FeignBusinessErrors.parse(cause, "ORDER_QUERY_UNAVAILABLE", "订单查询服务暂不可用，请稍后重试");
        };
    }
}
