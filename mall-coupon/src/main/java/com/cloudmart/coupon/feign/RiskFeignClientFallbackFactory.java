package com.cloudmart.coupon.feign;

import com.cloudmart.common.api.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 风控检查降级（RISK-01）：领券属非支付动作，风控不可用时**降级放行**
 * （方案："高风险支付依赖拒绝，普通动作可降级"）——但必须留告警日志可观测。
 */
@Slf4j
@Component
public class RiskFeignClientFallbackFactory implements FallbackFactory<RiskFeignClient> {

    @Override
    public RiskFeignClient create(Throwable cause) {
        log.warn("[RISK01] 风控服务不可用，领券风控检查降级放行: {}", cause.getMessage());
        return request -> ApiResponse.ok(Map.of("result", "PASS", "degraded", true));
    }
}
