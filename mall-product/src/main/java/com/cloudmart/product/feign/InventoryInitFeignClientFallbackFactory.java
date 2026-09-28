package com.cloudmart.product.feign;

import com.cloudmart.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 库存建档降级（CAT-01）：建档失败必须让发布失败——静默吞掉会留下
 * 无库存档案的可售商品（fail-closed，由调用方回滚发布事务）。
 */
@Slf4j
@Component
public class InventoryInitFeignClientFallbackFactory implements FallbackFactory<InventoryInitFeignClient> {

    @Override
    public InventoryInitFeignClient create(Throwable cause) {
        log.error("库存建档调用失败: {}", cause.getMessage());
        return (skuId, productId, stock) -> {
            throw new BusinessException("INVENTORY_SERVICE_UNAVAILABLE", "库存服务不可用，库存建档失败");
        };
    }
}
