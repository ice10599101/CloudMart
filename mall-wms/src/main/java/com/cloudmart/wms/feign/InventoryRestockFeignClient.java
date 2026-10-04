package com.cloudmart.wms.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 库存入库客户端（T19）：WMS 收货流水（PASSED）提交库存可售入账——
 * receiptId 幂等（inventory uk(inventory_logs.receipt_id) 兜底），失败重试安全。
 * 请求头由 ServiceTokenFeignInterceptor 自动签名（iss=mall-wms, scope=inventory:trade）。
 */
@FeignClient(contextId = "wmsInventoryRestockFeignClient", name = "mall-inventory")
public interface InventoryRestockFeignClient {

    @PostMapping("/internal/inventory/restock")
    ApiResponse<Map<String, Object>> restock(@RequestBody RestockRequest request);

    record RestockRequest(String receiptId, Long skuId, Integer quantity) {
    }
}
