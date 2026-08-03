package com.cloudmart.wms.mq;

import com.cloudmart.wms.config.RocketMQConfig;
import com.cloudmart.wms.dto.CreatePickOrderRequest;
import com.cloudmart.wms.service.PickOrderService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RocketMQMessageListener(
        topic = RocketMQConfig.ORDER_TOPIC,
        consumerGroup = RocketMQConfig.CG_WMS_ORDER_PAID,
        selectorExpression = RocketMQConfig.ORDER_TAG_PAID
)
public class OrderPaidListener implements RocketMQListener<Map<String, Object>> {

    private static final Logger log = LoggerFactory.getLogger(OrderPaidListener.class);

    private final PickOrderService pickOrderService;

    public OrderPaidListener(PickOrderService pickOrderService) {
        this.pickOrderService = pickOrderService;
    }

    @Override
    public void onMessage(Map<String, Object> message) {
        Long orderId = ((Number) message.get("orderId")).longValue();
        Long warehouseId = message.get("warehouseId") != null
                ? ((Number) message.get("warehouseId")).longValue()
                : 1L;

        log.info("Received order paid event, creating pick order: orderId={}, warehouseId={}", orderId, warehouseId);

        try {
            if (pickOrderService.findByOrderId(orderId) != null) {
                log.info("Pick order already exists for orderId={}, skipping", orderId);
                return;
            }

            CreatePickOrderRequest request = new CreatePickOrderRequest(orderId, warehouseId, "订单支付成功自动生成");
            pickOrderService.createPickOrder(request);
            log.info("Pick order created for orderId={}", orderId);
        } catch (Exception e) {
            log.error("Failed to create pick order for orderId={}: {}", orderId, e.getMessage());
        }
    }
}
