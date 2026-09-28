package com.cloudmart.order.feign;

import com.cloudmart.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 风控检查降级（RISK-01）：下单为高风险动作——风控不可用时**拒绝下单**
 * （fail-closed），由用户稍后重试；绝不"风控挂了就放行"。
 */
@Slf4j
@Component
public class RiskFeignClientFallbackFactory implements FallbackFactory<RiskFeignClient> {

    @Override
    public RiskFeignClient create(Throwable cause) {
        log.error("风控服务调用失败: {}", cause.getMessage());
        return request -> {
            throw new BusinessException("RISK_SERVICE_UNAVAILABLE", "风控服务暂不可用，下单被拒绝，请稍后重试");
        };
    }
}
