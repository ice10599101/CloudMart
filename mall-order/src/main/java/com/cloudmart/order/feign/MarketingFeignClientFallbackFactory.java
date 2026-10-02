package com.cloudmart.order.feign;

import com.cloudmart.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 拼团快照回查降级（T10）：fail-closed——查不到成团快照不能按普通价建单，
 * 抛错让 MQ 重试/恢复对账，绝不降级放行。
 */
@Slf4j
@Component
public class MarketingFeignClientFallbackFactory implements FallbackFactory<MarketingFeignClient> {

    @Override
    public MarketingFeignClient create(Throwable cause) {
        log.warn("[T10] 拼团快照回查不可用: {}", cause.getMessage());
        return groupOrderId -> {
            throw new BusinessException("GROUP_QUOTE_UNAVAILABLE", "拼团快照服务暂不可用，请稍后重试");
        };
    }
}
