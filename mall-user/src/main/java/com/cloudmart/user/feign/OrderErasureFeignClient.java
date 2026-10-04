package com.cloudmart.user.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * mall-order 订单去标识化端点（T06 注销编排）：交易/财务记录按保留策略留存，
 * 收货人 PII 就地脱敏（幂等）。请求头由 ServiceTokenFeignInterceptor 自动签名
 * （iss=mall-user，aud=mall-order，scope=order:internal）。
 */
@FeignClient(name = "mall-order", contextId = "userOrderErasureFeignClient")
public interface OrderErasureFeignClient {

    /** 幂等去标识化该用户订单的收货人信息；返回改写行数 */
    @PostMapping("/internal/orders/erasure/anonymize-receiver")
    ApiResponse<Integer> anonymizeReceiver(@RequestParam("userId") Long userId);
}
