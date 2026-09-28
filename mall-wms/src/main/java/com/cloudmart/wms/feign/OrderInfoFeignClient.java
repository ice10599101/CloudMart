package com.cloudmart.wms.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wms.dto.OrderInternalInfoDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 订单内部信息客户端（SEC-04）：用户按 orderId 查物流前，读取订单权威归属
 * 做对象归属校验（requireOwner）。服务令牌由出站拦截器按
 * {@code cloudmart.security.outbound-scopes[mall-order]} 签名。
 */
@FeignClient(contextId = "wmsOrderInfoFeignClient", name = "mall-order", path = "/internal/orders",
        fallbackFactory = OrderInfoFeignClientFallbackFactory.class)
public interface OrderInfoFeignClient {

    @GetMapping("/{orderId}")
    ApiResponse<OrderInternalInfoDTO> getOrderInfo(@PathVariable("orderId") Long orderId);
}
