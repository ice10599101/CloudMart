package com.cloudmart.order.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.order.dto.InventoryDeductRequest;
import com.cloudmart.order.dto.InventoryReleaseRequest;
import com.cloudmart.order.feign.InventoryFeignClient.InventoryDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class InventoryFeignClientFallbackFactory implements FallbackFactory<InventoryFeignClient> {

    @Override
    public InventoryFeignClient create(Throwable cause) {
        log.error("库存服务调用失败: {}", cause.getMessage());
        return new InventoryFeignClient() {
            @Override
            public ApiResponse<Boolean> deductStock(InventoryDeductRequest request) {
                throw new com.cloudmart.common.exception.BusinessException(
                        "INVENTORY_SERVICE_UNAVAILABLE", "库存服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> releaseStock(InventoryReleaseRequest request) {
                log.error("释放库存降级跳过, skuId={}: {}", request.skuId(), cause.getMessage());
                return ApiResponse.ok(null);
            }

            @Override
            public ApiResponse<InventoryDTO> getInventory(Long skuId) {
                throw new com.cloudmart.common.exception.BusinessException(
                        "INVENTORY_SERVICE_UNAVAILABLE", "库存服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> confirmDeduct(Long skuId, Integer quantity, Long orderId) {
                log.error("确认扣减降级跳过, skuId={}: {}", skuId, cause.getMessage());
                return ApiResponse.ok(null);
            }
        };
    }
}
