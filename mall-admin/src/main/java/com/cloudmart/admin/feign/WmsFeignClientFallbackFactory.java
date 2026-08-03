package com.cloudmart.admin.feign;

import com.cloudmart.admin.dto.feign.WmsSearchRequest;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class WmsFeignClientFallbackFactory implements FallbackFactory<WmsFeignClient> {

    @Override
    public WmsFeignClient create(Throwable cause) {
        log.error("仓储服务调用失败: {}", cause.getMessage());
        return new WmsFeignClient() {
            @Override
            public ApiResponse<Object> listPickOrders(WmsSearchRequest request) {
                throw new BusinessException("WMS_SERVICE_UNAVAILABLE", "仓储服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> getPickOrder(Long id) {
                throw new BusinessException("WMS_SERVICE_UNAVAILABLE", "仓储服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> startPick(Long id, Long assignedUserId) {
                throw new BusinessException("WMS_SERVICE_UNAVAILABLE", "仓储服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> confirmPicked(Long id) {
                throw new BusinessException("WMS_SERVICE_UNAVAILABLE", "仓储服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> confirmPacked(Long id) {
                throw new BusinessException("WMS_SERVICE_UNAVAILABLE", "仓储服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> listInboundOrders(WmsSearchRequest request) {
                throw new BusinessException("WMS_SERVICE_UNAVAILABLE", "仓储服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> getInboundOrder(Long id) {
                throw new BusinessException("WMS_SERVICE_UNAVAILABLE", "仓储服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> listWarehouses() {
                throw new BusinessException("WMS_SERVICE_UNAVAILABLE", "仓储服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> listShipping(WmsSearchRequest request) {
                throw new BusinessException("WMS_SERVICE_UNAVAILABLE", "仓储服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> updateShippingStatus(Long id, Map<String, Object> body) {
                throw new BusinessException("WMS_SERVICE_UNAVAILABLE", "仓储服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> createWarehouse(Map<String, Object> body) {
                throw new BusinessException("WMS_SERVICE_UNAVAILABLE", "仓储服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> updateWarehouse(Long id, Map<String, Object> body) {
                throw new BusinessException("WMS_SERVICE_UNAVAILABLE", "仓储服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> deleteWarehouse(Long id) {
                throw new BusinessException("WMS_SERVICE_UNAVAILABLE", "仓储服务不可用，请稍后重试");
            }
        };
    }
}
