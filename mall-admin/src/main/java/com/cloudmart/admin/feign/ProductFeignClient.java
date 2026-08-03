package com.cloudmart.admin.feign;

import com.cloudmart.admin.dto.feign.*;
import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.*;

@FeignClient(contextId = "productFeignClient", name = "mall-product", path = "/admin/products", fallbackFactory = ProductFeignClientFallbackFactory.class)
public interface ProductFeignClient {

    @GetMapping("/search")
    ApiResponse<ProductSearchResultDTO> searchProducts(@SpringQueryMap ProductSearchRequest request);

    @GetMapping("/{id}")
    ApiResponse<ProductDTO> getProductById(@PathVariable("id") Long id);

    @PostMapping
    ApiResponse<ProductDTO> createProduct(@RequestBody CreateProductRequest request);

    @PutMapping("/{id}")
    ApiResponse<ProductDTO> updateProduct(@PathVariable("id") Long id, @RequestBody UpdateProductRequest request);

    @DeleteMapping("/{id}")
    ApiResponse<Void> deleteProduct(@PathVariable("id") Long id);

    @GetMapping("/count")
    ApiResponse<CountResponse> getProductCount();
}
