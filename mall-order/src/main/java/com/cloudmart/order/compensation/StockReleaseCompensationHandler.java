package com.cloudmart.order.compensation;

import com.cloudmart.common.async.compensation.CompensationHandler;
import com.cloudmart.order.dto.InventoryReleaseRequest;
import com.cloudmart.order.feign.InventoryFeignClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 库存释放补偿处理器（ASYNC-01）：取消订单后释放失败的持久化重试。
 * 载荷：{"orderId":x,"skuId":y,"quantity":z}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockReleaseCompensationHandler implements CompensationHandler {

    private final InventoryFeignClient inventoryFeignClient;
    private final ObjectMapper objectMapper;

    @Override
    public String action() {
        return "stock-release";
    }

    @Override
    public void execute(String aggregateId, String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            inventoryFeignClient.releaseStock(new InventoryReleaseRequest(
                    node.get("skuId").asLong(), node.get("quantity").asInt(), node.get("orderId").asLong()));
        } catch (Exception e) {
            throw new IllegalStateException("stock-release failed for order " + aggregateId + ": " + e.getMessage(), e);
        }
    }
}
