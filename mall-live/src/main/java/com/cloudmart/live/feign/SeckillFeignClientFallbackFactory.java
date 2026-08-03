package com.cloudmart.live.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class SeckillFeignClientFallbackFactory implements FallbackFactory<SeckillFeignClient> {

    @Override
    public SeckillFeignClient create(Throwable cause) {
        log.error("秒杀服务调用失败: {}", cause.getMessage());
        return new SeckillFeignClient() {
            @Override
            public ApiResponse<Map<String, Object>> executeSeckill(Long userId, Long activityId) {
                throw new BusinessException("SECKILL_SERVICE_UNAVAILABLE", "秒杀服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Map<String, Object>> getSeckillActivity(Long activityId, String internalCall) {
                throw new BusinessException("SECKILL_SERVICE_UNAVAILABLE", "秒杀服务不可用，请稍后重试");
            }
        };
    }
}
