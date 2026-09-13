package com.cloudmart.admin.feign;

import com.cloudmart.admin.dto.feign.*;
import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(contextId = "productFeignClient", name = "mall-product", path = "/admin/products", fallbackFactory = ProductFeignClientFallbackFactory.class)
public interface ProductFeignClient {

    @GetMapping("/search")
    ApiResponse<ProductSearchResultDTO> searchProducts(@RequestParam(value = "keyword", required = false) String keyword,
                                                       @RequestParam(value = "categoryId", required = false) Long categoryId,
                                                       @RequestParam(value = "minPrice", required = false) java.math.BigDecimal minPrice,
                                                       @RequestParam(value = "maxPrice", required = false) java.math.BigDecimal maxPrice,
                                                       @RequestParam(value = "sort", required = false) String sort,
                                                       @RequestParam(value = "status", required = false) Integer status,
                                                       @RequestParam("page") Integer page,
                                                       @RequestParam("size") Integer size);

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
