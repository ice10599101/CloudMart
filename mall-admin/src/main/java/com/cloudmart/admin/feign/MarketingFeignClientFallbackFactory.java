package com.cloudmart.admin.feign;

import com.cloudmart.admin.dto.feign.*;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class MarketingFeignClientFallbackFactory implements FallbackFactory<MarketingFeignClient> {

    @Override
    public MarketingFeignClient create(Throwable cause) {
        log.error("营销服务调用失败: {}", cause.getMessage());
        return new MarketingFeignClient() {
            @Override
            public ApiResponse<Object> listGroupActivities(GroupActivitySearchRequest request) {
                throw new BusinessException("MARKETING_SERVICE_UNAVAILABLE", "营销服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<GroupActivityDTO> createGroupActivity(CreateGroupActivityRequest request) {
                throw new BusinessException("MARKETING_SERVICE_UNAVAILABLE", "营销服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> updateGroupActivity(Long id, Map<String, Object> body) {
                throw new BusinessException("MARKETING_SERVICE_UNAVAILABLE", "营销服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<GroupActivityDTO> enableGroupActivity(Long id) {
                throw new BusinessException("MARKETING_SERVICE_UNAVAILABLE", "营销服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<GroupActivityDTO> disableGroupActivity(Long id) {
                throw new BusinessException("MARKETING_SERVICE_UNAVAILABLE", "营销服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> deleteGroupActivity(Long id) {
                throw new BusinessException("MARKETING_SERVICE_UNAVAILABLE", "营销服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> listGroupOrders(GroupOrderSearchRequest request) {
                throw new BusinessException("MARKETING_SERVICE_UNAVAILABLE", "营销服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> listTieredPromotions(TieredPromotionSearchRequest request) {
                throw new BusinessException("MARKETING_SERVICE_UNAVAILABLE", "营销服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<TieredPromotionDTO> createTieredPromotion(CreateTieredPromotionRequest request) {
                throw new BusinessException("MARKETING_SERVICE_UNAVAILABLE", "营销服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> updateTieredPromotion(Long id, Map<String, Object> body) {
                throw new BusinessException("MARKETING_SERVICE_UNAVAILABLE", "营销服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<TieredPromotionDTO> enableTieredPromotion(Long id) {
                throw new BusinessException("MARKETING_SERVICE_UNAVAILABLE", "营销服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<TieredPromotionDTO> disableTieredPromotion(Long id) {
                throw new BusinessException("MARKETING_SERVICE_UNAVAILABLE", "营销服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<TieredPromotionDTO> getTieredPromotion(Long id) {
                throw new BusinessException("MARKETING_SERVICE_UNAVAILABLE", "营销服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> deleteTieredPromotion(Long id) {
                throw new BusinessException("MARKETING_SERVICE_UNAVAILABLE", "营销服务不可用，请稍后重试");
            }
        };
    }
}
