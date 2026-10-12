package com.cloudmart.community.feign;

import com.cloudmart.common.api.ApiResponse;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * R22 用户业务时区查询（mall-wish 权威来源 wish_user_stat.timezone）。
 * 独立 contextId 避免与 mall-user 的 communityUserFeignClient 冲突。
 */
@FeignClient(name = "mall-wish", contextId = "communityWishUserProfileFeignClient",
        fallbackFactory = WishUserProfileFeignClientFallbackFactory.class)
public interface WishUserProfileFeignClient {

    @GetMapping("/internal/users/{userId}/timezone")
    ApiResponse<Map<String, String>> getTimezone(@PathVariable("userId") Long userId);
}
