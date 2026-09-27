package com.cloudmart.user.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * mall-wish 账号数据擦除端点（B20 编排调用）：服务令牌由编排服务在调用时
 * 经 {@code ServiceTokenSigner} 签出（iss=mall-user，scope=wish:erasure）。
 */
@FeignClient(name = "mall-wish", contextId = "userErasureFeignClient")
public interface ErasureFeignClient {

    /** 幂等擦除该用户在心愿宇宙的全部数据；重复调用返回原结果 */
    @PostMapping("/internal/account-erasure")
    ApiResponse<Boolean> eraseWishData(@RequestParam("userId") Long userId,
                                       @RequestHeader("X-Service-Token") String serviceToken);
}
