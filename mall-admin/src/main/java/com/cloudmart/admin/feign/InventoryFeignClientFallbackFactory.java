package com.cloudmart.admin.feign;

import com.cloudmart.admin.dto.feign.InventoryDTO;
import com.cloudmart.admin.dto.feign.InventorySearchRequest;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class InventoryFeignClientFallbackFactory implements FallbackFactory<InventoryFeignClient> {

    @Override
    public InventoryFeignClient create(Throwable cause) {
        log.error("库存服务调用失败: {}", cause.getMessage());
        return new InventoryFeignClient() {
            @Override
            public ApiResponse<List<InventoryDTO>> listInventory(InventorySearchRequest request) {
                throw new BusinessException("INVENTORY_SERVICE_UNAVAILABLE", "库存服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<InventoryDTO> getInventory(Long skuId) {
                throw new BusinessException("INVENTORY_SERVICE_UNAVAILABLE", "库存服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> initStock(Long skuId, Long productId, Integer stock) {
                throw new BusinessException("INVENTORY_SERVICE_UNAVAILABLE", "库存服务不可用，请稍后重试");
            }
        };
    }
}
