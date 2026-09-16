package com.cloudmart.user.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * mall-community Feign 客户端（他人资料敏感字段脱敏专用）。
 *
 * <p>走 mall-community 内部端点 /internal/privacy/**，鉴权由 Feign 拦截器
 * 统一注入 {@code X-Internal-Call: true} 与 {@code X-User-Id: <查看者ID>}。</p>
 */
@FeignClient(name = "mall-community", contextId = "userCommunityFeignClient",
        fallbackFactory = CommunityFeignClientFallbackFactory.class)
public interface CommunityFeignClient {

    /**
     * 查询目标用户生日/邮箱对查看者的可见性。
     *
     * @return ApiResponse.data 含 {birthdayVisible, emailVisible}
     */
    @GetMapping("/internal/privacy/{targetUserId}")
    ApiResponse<Map<String, Object>> getPrivacyVisibility(@PathVariable("targetUserId") Long targetUserId);
}