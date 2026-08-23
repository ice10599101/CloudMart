package com.cloudmart.admin.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

/**
 * mall-wish 时间胶囊管理端 Feign 客户端（Sprint 2.4）。
 *
 * <p>下游 /admin/capsules/** 由 @PreAuthorize("hasRole('INTERNAL')") 保护，
 * X-Internal-Call 头由 AdminFeignInterceptor 全局注入。</p>
 */
@FeignClient(contextId = "capsuleFeignClient", name = "mall-wish", path = "/admin/capsules",
        fallbackFactory = CapsuleFeignClientFallbackFactory.class)
public interface CapsuleFeignClient {

    @GetMapping("/stats")
    ApiResponse<Object> getStats();
}
