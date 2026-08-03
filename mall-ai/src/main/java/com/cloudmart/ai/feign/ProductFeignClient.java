package com.cloudmart.ai.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 商品服务远程调用客户端，用于获取商品详情和评论数据。
 */
@FeignClient(name = "mall-product", contextId = "aiProductClient", fallbackFactory = ProductFeignClientFallbackFactory.class)
public interface ProductFeignClient {

    @GetMapping("/products/{id}")
    ApiResponse<Map<String, Object>> getProduct(
            @PathVariable("id") Long id,
            @RequestHeader("X-Internal-Call") String internalCall
    );

    @GetMapping("/products/{productId}/reviews")
    ApiResponse<Map<String, Object>> getReviews(
            @PathVariable("productId") Long productId,
            @RequestParam("page") int page,
            @RequestParam("size") int size,
            @RequestHeader("X-Internal-Call") String internalCall
    );
}
