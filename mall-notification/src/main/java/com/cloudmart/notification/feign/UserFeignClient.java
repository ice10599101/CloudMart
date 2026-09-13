package com.cloudmart.notification.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "mall-user", contextId = "notificationUserFeignClient", fallbackFactory = UserFeignClientFallbackFactory.class)
public interface UserFeignClient {

    @GetMapping("/users/batch")
    ApiResponse<List<Map<String, Object>>> batchGetUsers(@RequestParam("ids") List<Long> ids);

    /**
     * 分页枚举全量会员（mall-user GET /admin/users，INTERNAL 鉴权）。
     * 仅广播场景用于提取用户 ID，返回 Map 以匹配现有 batchGetUsers 的宽松契约风格。
     */
    @GetMapping("/admin/users")
    ApiResponse<List<Map<String, Object>>> listUsers(@RequestParam("page") int page, @RequestParam("size") int size);
}
