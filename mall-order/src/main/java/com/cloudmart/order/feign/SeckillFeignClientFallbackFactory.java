package com.cloudmart.order.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.feign.FeignBusinessErrors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 报价回查降级（T09）：fail-closed——查不到冻结快照不能按普通价建单
 * （秒杀价与普通价不混用），抛错让消费重试/恢复对账，绝不降级放行。
 */
@Slf4j
@Component
public class SeckillFeignClientFallbackFactory implements FallbackFactory<SeckillFeignClient> {

    @Override
    public SeckillFeignClient create(Throwable cause) {
        log.warn("[T09] 秒杀报价回查不可用: {}", cause.getMessage());
        return requestId -> {
            throw FeignBusinessErrors.parse(cause, "SECKILL_QUOTE_UNAVAILABLE", "秒杀报价服务暂不可用，请稍后重试");
        };
    }
}
