package com.cloudmart.seckill.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * mall-order 内部订单查询客户端（T09 恢复链路）：恢复任务按请求事实对账——
 * 排队超时的请求先查订单是否存在（requestId 即订单 request_key），
 * 存在则补落 SUCCESS，不存在才终态失败并释放占用。
 */
@FeignClient(contextId = "orderQueryFeignClient", name = "mall-order",
        fallbackFactory = OrderQueryFeignClientFallbackFactory.class)
public interface OrderQueryFeignClient {

    /** @return data=orderId；订单不存在时 data=null */
    @GetMapping("/internal/orders/by-request/{requestId}")
    ApiResponse<Long> findOrderIdByRequestId(@PathVariable("requestId") String requestId);
}
