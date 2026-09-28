package com.cloudmart.product.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 库存建档客户端（CAT-01）：库存服务为唯一库存权威——商品发布时为新 SKU
 * 建立库存档案，建档失败则发布失败（防止出现无库存档案的可售商品）。
 * 出站服务令牌按 {@code outbound-scopes[mall-inventory]=inventory:admin} 签名。
 */
@FeignClient(contextId = "productInventoryFeignClient", name = "mall-inventory", path = "/inventory",
        fallbackFactory = InventoryInitFeignClientFallbackFactory.class)
public interface InventoryInitFeignClient {

    @PostMapping("/init")
    ApiResponse<Void> initStock(@RequestParam("skuId") Long skuId,
                                @RequestParam("productId") Long productId,
                                @RequestParam("stock") Integer stock);
}
