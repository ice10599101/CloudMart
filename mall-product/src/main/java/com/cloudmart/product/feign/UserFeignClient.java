package com.cloudmart.product.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * N-5 问大家：用户昵称批量解析（展示型数据，fail-open 由 fallback 兜空）。
 * mall-user /users/batch 为登录公开端点，无需服务令牌。
 */
@FeignClient(name = "mall-user", contextId = "productUserFeignClient",
        fallbackFactory = UserFeignClientFallbackFactory.class)
public interface UserFeignClient {

    @GetMapping("/users/batch")
    ApiResponse<List<Map<String, Object>>> batchGetUsers(@RequestParam("ids") List<Long> ids);
}
