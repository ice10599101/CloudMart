package com.cloudmart.admin.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 秒杀请求运营查询降级（T09）：fail-closed 显式失败。
 */
@Slf4j
@Component
public class SeckillRequestFeignClientFallbackFactory implements FallbackFactory<SeckillRequestFeignClient> {

    @Override
    public SeckillRequestFeignClient create(Throwable cause) {
        log.error("秒杀请求运营查询调用失败: {}", cause.getMessage());
        return (page, pageSize, status, activityId, userId) -> {
            throw new BusinessException("SECKILL_SERVICE_UNAVAILABLE", "秒杀服务不可用，请稍后重试");
        };
    }
}
