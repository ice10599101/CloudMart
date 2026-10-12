package com.cloudmart.pet.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.pet.config.WishProfileTokenConfig;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * R22 用户业务时区查询（mall-wish wish_user_stat 权威来源），scope=wish:profile。
 */
@FeignClient(name = "mall-wish", contextId = "petWishUserProfileFeignClient",
        configuration = WishProfileTokenConfig.class,
        fallbackFactory = WishUserProfileFeignClientFallbackFactory.class)
public interface WishUserProfileFeignClient {

    @GetMapping("/internal/users/{userId}/timezone")
    ApiResponse<Map<String, String>> getTimezone(@PathVariable("userId") Long userId);
}
