package com.cloudmart.order.compensation;

import com.cloudmart.common.async.compensation.CompensationHandler;
import com.cloudmart.order.feign.InventoryFeignClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 库存确认补偿处理器（ASYNC-01）：支付成功后确认扣减失败的持久化重试。
 * 载荷：{"orderId":x,"skuId":y,"quantity":z}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockConfirmCompensationHandler implements CompensationHandler {

    private final InventoryFeignClient inventoryFeignClient;
    private final ObjectMapper objectMapper;

    @Override
    public String action() {
        return "stock-confirm";
    }

    @Override
    public void execute(String aggregateId, String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            inventoryFeignClient.confirmDeduct(node.get("skuId").asLong(), node.get("quantity").asInt(),
                    node.get("orderId").asLong());
        } catch (Exception e) {
            throw new IllegalStateException("stock-confirm failed for order " + aggregateId + ": " + e.getMessage(), e);
        }
    }
}
