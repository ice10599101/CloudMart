package com.cloudmart.admin.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(contextId = "aiFeignClient", name = "mall-ai",
        fallbackFactory = AiFeignClientFallbackFactory.class)
public interface AiFeignClient {

    @PostMapping("/chat")
    ApiResponse<Map<String, Object>> chat(@RequestHeader("X-User-Id") Long userId,
                                           @RequestBody Map<String, String> request);

    @GetMapping("/search")
    ApiResponse<Map<String, Object>> search(@RequestHeader("X-User-Id") Long userId,
                                             @RequestParam("query") String query);

    @PostMapping("/admin/vector-sync/full")
    ApiResponse<Void> triggerFullSync();

    @PostMapping("/admin/vector-sync/product/{productId}")
    ApiResponse<Void> syncProduct(@PathVariable("productId") Long productId);

    @DeleteMapping("/admin/vector-sync/product/{productId}")
    ApiResponse<Void> deleteProductVector(@PathVariable("productId") Long productId);
}
