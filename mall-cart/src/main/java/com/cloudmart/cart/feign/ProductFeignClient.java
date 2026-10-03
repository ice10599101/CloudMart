package com.cloudmart.cart.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(contextId = "cartProductFeignClient", name = "mall-product", path = "/products", fallbackFactory = ProductFeignClientFallbackFactory.class)
public interface ProductFeignClient {

    @GetMapping("/{id}")
    ApiResponse<ProductInfo> getProductById(@PathVariable("id") Long id);

    /** T08：购物车失效项校验用——一次批量取回全部 SKU 权威状态/价格 */
    @GetMapping("/skus/batch")
    ApiResponse<List<Map<String, Object>>> getSkusBatch(@RequestParam("ids") List<Long> ids);

    record SkuInfo(Long id, String skuCode, String attributes, java.math.BigDecimal price,
                   java.math.BigDecimal originalPrice, Integer stock, String image, Integer status) {}

    record ProductInfo(Long id, String name, String mainImage, java.util.List<SkuInfo> skus) {}
}
