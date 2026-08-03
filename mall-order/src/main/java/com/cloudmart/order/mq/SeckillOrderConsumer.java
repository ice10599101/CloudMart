package com.cloudmart.order.mq;

import com.cloudmart.order.config.RocketMQConfig;
import com.cloudmart.order.dto.CreateOrderRequest;
import com.cloudmart.order.dto.OrderDTO;
import com.cloudmart.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = RocketMQConfig.SECKILL_TOPIC,
        consumerGroup = RocketMQConfig.CG_ORDER_SECKILL,
        selectorExpression = RocketMQConfig.SECKILL_TAG_ORDER
)
public class SeckillOrderConsumer implements RocketMQListener<Map<String, Object>> {

    private final OrderService orderService;

    @Override
    public void onMessage(Map<String, Object> message) {
        Long userId = ((Number) message.get("userId")).longValue();
        Long activityId = ((Number) message.get("activityId")).longValue();
        Long skuId = ((Number) message.get("skuId")).longValue();
        Long productId = ((Number) message.get("seckillProductId")).longValue();
        Integer quantity = message.get("quantity") != null ? ((Number) message.get("quantity")).intValue() : 1;

        log.info("Processing seckill order: userId={}, activityId={}, skuId={}", userId, activityId, skuId);

        try {
            CreateOrderRequest.OrderItemInput item = new CreateOrderRequest.OrderItemInput(
                    productId, skuId, quantity, null, null, null, null
            );
            CreateOrderRequest request = new CreateOrderRequest(
                    UUID.randomUUID().toString(), List.of(item), null, null, null, null, activityId
            );
            OrderDTO order = orderService.createOrder(userId, request);
            log.info("Seckill order created: orderId={}, userId={}", order.id(), userId);
        } catch (Exception e) {
            log.error("Failed to create seckill order: userId={}, activityId={}", userId, activityId, e);
        }
    }
}
