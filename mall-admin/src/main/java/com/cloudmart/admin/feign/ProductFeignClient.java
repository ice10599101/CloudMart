package com.cloudmart.admin.feign;

import com.cloudmart.admin.dto.feign.*;
import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;

import java.util.List;
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

    /** TRADE-02：按 SKU ID 批量取商品名/规格（库存列表补齐展示字段，单次调用避免 N+1） */
    @GetMapping("/skus/batch")
    ApiResponse<List<SkuBatchDTO>> getSkusBatch(@RequestParam("ids") List<Long> ids);

    // ===== PRODUCT-01：商品问答管理 =====

    @GetMapping("/questions")
    ApiResponse<Object> adminQuestions(@RequestParam(value = "productId", required = false) Long productId,
                                       @RequestParam(value = "status", required = false) Integer status,
                                       @RequestParam("page") Integer page,
                                       @RequestParam("size") Integer size);

    @PostMapping("/questions/{questionId}/hide")
    ApiResponse<Void> hideQuestion(@PathVariable("questionId") Long questionId,
                                   @RequestBody java.util.Map<String, String> body);

    @PostMapping("/questions/{questionId}/restore")
    ApiResponse<Void> restoreQuestion(@PathVariable("questionId") Long questionId);

    @PostMapping("/answers/{answerId}/hide")
    ApiResponse<Void> hideAnswer(@PathVariable("answerId") Long answerId,
                                 @RequestBody java.util.Map<String, String> body);

    @PostMapping("/answers/{answerId}/restore")
    ApiResponse<Void> restoreAnswer(@PathVariable("answerId") Long answerId);
}
