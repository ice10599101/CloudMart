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
                // ASYNC-01 断点 4：fallback 不得伪装成功——调用方依赖失败信号登记补偿，
                // 返回 ok(null) 会让释放结果丢失且无恢复待办（fail-closed）
                log.error("释放库存降级失败, skuId={}: {}", request.skuId(), cause.getMessage());
                throw new com.cloudmart.common.exception.BusinessException(
                        "INVENTORY_SERVICE_UNAVAILABLE", "库存服务不可用，释放操作失败");
            }

            @Override
            public ApiResponse<InventoryDTO> getInventory(Long skuId) {
                throw new com.cloudmart.common.exception.BusinessException(
                        "INVENTORY_SERVICE_UNAVAILABLE", "库存服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> confirmDeduct(Long skuId, Integer quantity, Long orderId) {
                // ASYNC-01 断点 4：同 releaseStock——失败必须显式，交给补偿任务恢复
                log.error("确认扣减降级失败, skuId={}, orderId={}: {}", skuId, orderId, cause.getMessage());
                throw new com.cloudmart.common.exception.BusinessException(
                        "INVENTORY_SERVICE_UNAVAILABLE", "库存服务不可用，确认操作失败");
            }
        };
    }
}
