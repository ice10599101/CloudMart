package com.cloudmart.coupon.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 风控检查客户端（RISK-01）：领券前置检查（黑名单/频次规则）。
 * 出站服务令牌按 {@code outbound-scopes[mall-risk]=risk:check} 签名。
 */
@FeignClient(contextId = "couponRiskFeignClient", name = "mall-risk", path = "/check",
        fallbackFactory = RiskFeignClientFallbackFactory.class)
public interface RiskFeignClient {

    @PostMapping("/internal")
    ApiResponse<Map<String, Object>> check(@RequestBody Map<String, Object> request);
}
