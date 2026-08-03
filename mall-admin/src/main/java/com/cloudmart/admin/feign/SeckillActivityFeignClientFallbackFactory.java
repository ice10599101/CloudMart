package com.cloudmart.admin.feign;

import com.cloudmart.admin.dto.feign.CreateActivityRequest;
import com.cloudmart.admin.dto.feign.SeckillActivityDTO;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class SeckillActivityFeignClientFallbackFactory implements FallbackFactory<SeckillActivityFeignClient> {

    @Override
    public SeckillActivityFeignClient create(Throwable cause) {
        log.error("秒杀服务调用失败: {}", cause.getMessage());
        return new SeckillActivityFeignClient() {
            @Override
            public ApiResponse<List<SeckillActivityDTO>> listActivities(String status) {
                throw new BusinessException("SECKILL_SERVICE_UNAVAILABLE", "秒杀服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<SeckillActivityDTO> getActivity(Long activityId) {
                throw new BusinessException("SECKILL_SERVICE_UNAVAILABLE", "秒杀服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<SeckillActivityDTO> createActivity(CreateActivityRequest request) {
                throw new BusinessException("SECKILL_SERVICE_UNAVAILABLE", "秒杀服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> updateActivity(Long activityId, Map<String, Object> body) {
                throw new BusinessException("SECKILL_SERVICE_UNAVAILABLE", "秒杀服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<SeckillActivityDTO> updateActivityStatus(Long activityId, String status) {
                throw new BusinessException("SECKILL_SERVICE_UNAVAILABLE", "秒杀服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> deleteActivity(Long activityId) {
                throw new BusinessException("SECKILL_SERVICE_UNAVAILABLE", "秒杀服务不可用，请稍后重试");
            }
        };
    }
}
