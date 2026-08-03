package com.cloudmart.admin.feign;

import com.cloudmart.admin.dto.feign.AddSeckillProductRequest;
import com.cloudmart.admin.dto.feign.SeckillProductDTO;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class SeckillProductFeignClientFallbackFactory implements FallbackFactory<SeckillProductFeignClient> {

    @Override
    public SeckillProductFeignClient create(Throwable cause) {
        log.error("秒杀服务调用失败: {}", cause.getMessage());
        return new SeckillProductFeignClient() {
            @Override
            public ApiResponse<List<SeckillProductDTO>> listProductsByActivity(Long activityId) {
                throw new BusinessException("SECKILL_SERVICE_UNAVAILABLE", "秒杀服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<SeckillProductDTO> getProduct(Long productId) {
                throw new BusinessException("SECKILL_SERVICE_UNAVAILABLE", "秒杀服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<SeckillProductDTO> addProduct(Long activityId, AddSeckillProductRequest request) {
                throw new BusinessException("SECKILL_SERVICE_UNAVAILABLE", "秒杀服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> deleteProduct(Long productId) {
                throw new BusinessException("SECKILL_SERVICE_UNAVAILABLE", "秒杀服务不可用，请稍后重试");
            }
        };
    }
}
