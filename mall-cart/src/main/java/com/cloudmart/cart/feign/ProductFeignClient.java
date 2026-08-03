package com.cloudmart.cart.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(contextId = "cartProductFeignClient", name = "mall-product", path = "/products", fallbackFactory = ProductFeignClientFallbackFactory.class)
public interface ProductFeignClient {

    @GetMapping("/{id}")
    ApiResponse<ProductInfo> getProductById(@PathVariable("id") Long id);

    record SkuInfo(Long id, String skuCode, String attributes, java.math.BigDecimal price,
                   java.math.BigDecimal originalPrice, Integer stock, String image, Integer status) {}

    record ProductInfo(Long id, String name, String mainImage, java.util.List<SkuInfo> skus) {}
}
