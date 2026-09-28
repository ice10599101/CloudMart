package com.cloudmart.order.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 物流包裹客户端（WMS-01 闭环）：管理端发货必须先在 WMS 建立真实包裹
 * （运单号必填）再出库——出库由 WMS 发布 ORDER_SHIPPED 事件回推订单推进。
 * 出站服务令牌按 {@code outbound-scopes[mall-wms]=wms:admin} 签名。
 */
@FeignClient(contextId = "orderWmsShippingFeignClient", name = "mall-wms", path = "/shipping",
        fallbackFactory = WmsShippingFeignClientFallbackFactory.class)
public interface WmsShippingFeignClient {

    @PostMapping
    ApiResponse<Map<String, Object>> createShipping(@RequestBody Map<String, Object> request);

    @PutMapping("/{id}/status")
    ApiResponse<Map<String, Object>> updateStatus(@PathVariable("id") Long id,
                                                  @RequestParam("status") String status);
}
