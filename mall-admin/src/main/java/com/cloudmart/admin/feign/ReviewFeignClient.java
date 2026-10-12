package com.cloudmart.admin.feign;

import com.cloudmart.admin.dto.feign.ReviewSearchRequest;
import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(contextId = "reviewFeignClient", name = "mall-product", path = "/admin/reviews", fallbackFactory = ReviewFeignClientFallbackFactory.class)
public interface ReviewFeignClient {

    @GetMapping
    ApiResponse<Object> listReviews(@RequestParam(value = "productId", required = false) Long productId,
                                    @RequestParam(value = "status", required = false) Integer status,
                                    @RequestParam("page") Integer page,
                                    @RequestParam("pageSize") Integer pageSize);

    @GetMapping("/{id}")
    ApiResponse<Object> getReview(@PathVariable("id") Long id);

    /** R08：审核/下架（status=0 必带 reason），请求体透传 mall-product */
    @PutMapping("/{id}/status")
    ApiResponse<Void> updateReviewStatus(@PathVariable("id") Long id,
                                         @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> body);

    @DeleteMapping("/{id}")
    ApiResponse<Void> deleteReview(@PathVariable("id") Long id);

    @GetMapping("/stats/{productId}")
    ApiResponse<Object> getReviewStats(@PathVariable("productId") Long productId);
}
