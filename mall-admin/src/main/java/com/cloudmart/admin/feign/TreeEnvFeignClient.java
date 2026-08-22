package com.cloudmart.admin.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * mall-wish 生命树环境管理端 Feign 客户端（Sprint 2.2）。
 *
 * <p>下游 /admin/tree-env/** 由 @PreAuthorize("hasRole('INTERNAL')") 保护；
 * 触发特殊事件的 X-User-Id（操作管理员）由 AdminFeignInterceptor 统一
 * 透传，无需方法参数显式声明。</p>
 */
@FeignClient(contextId = "treeEnvFeignClient", name = "mall-wish", path = "/admin/tree-env",
        fallbackFactory = TreeEnvFeignClientFallbackFactory.class)
public interface TreeEnvFeignClient {

    // ========== 特殊事件触发台 ==========

    @PostMapping("/special-events")
    ApiResponse<Object> triggerSpecialEvent(@RequestBody Map<String, Object> data);

    @PutMapping("/special-events/{id}/end")
    ApiResponse<Object> endSpecialEvent(@PathVariable("id") Long eventId);

    @GetMapping("/special-events")
    ApiResponse<Object> listSpecialEvents(@RequestParam("limit") int limit);

    // ========== 环境配置管理（表化） ==========

    @GetMapping("/configs")
    ApiResponse<Object> listEnvConfigs();

    @PostMapping("/configs")
    ApiResponse<Object> createEnvConfig(@RequestBody Map<String, Object> data);

    @PutMapping("/configs/{id}")
    ApiResponse<Object> updateEnvConfig(@PathVariable("id") Long configId,
                                        @RequestBody Map<String, Object> data);

    @PutMapping("/configs/{id}/status")
    ApiResponse<Object> updateEnvConfigStatus(@PathVariable("id") Long configId,
                                              @RequestBody Map<String, Object> data);
}
