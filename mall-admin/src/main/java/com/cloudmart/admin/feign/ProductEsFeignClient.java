package com.cloudmart.admin.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Map;

/**
 * T08：商品 ES 索引管理代理客户端（蓝绿重建/版本清单）——
 * 索引端点在 mall-product 为 INTERNAL（服务令牌），经本代理以管理端权限收口。
 */
@FeignClient(contextId = "productEsFeignClient", name = "mall-product", path = "/products/es",
        fallbackFactory = ProductEsFeignClientFallbackFactory.class)
public interface ProductEsFeignClient {

    @GetMapping("/index/status")
    ApiResponse<Map<String, Object>> indexStatus();

    @GetMapping("/index/versions")
    ApiResponse<Map<String, Object>> indexVersions();

    @PostMapping("/index/full-rebuild")
    ApiResponse<Map<String, Object>> fullRebuild();
}
