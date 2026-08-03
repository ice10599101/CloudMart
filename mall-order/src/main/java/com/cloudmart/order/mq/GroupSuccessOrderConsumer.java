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
        topic = RocketMQConfig.MARKETING_TOPIC,
        consumerGroup = RocketMQConfig.CG_ORDER_GROUP_SUCCESS,
        selectorExpression = RocketMQConfig.MARKETING_TAG_GROUP_SUCCESS
)
public class GroupSuccessOrderConsumer implements RocketMQListener<Map<String, Object>> {

    private final OrderService orderService;

    @Override
    public void onMessage(Map<String, Object> message) {
        Long activityId = ((Number) message.get("activityId")).longValue();
        Long productId = ((Number) message.get("productId")).longValue();
        Long skuId = ((Number) message.get("skuId")).longValue();
        @SuppressWarnings("unchecked")
        List<Number> memberUserIds = (List<Number>) message.get("memberUserIds");

        log.info("Processing group success: activityId={}, skuId={}, members={}", activityId, skuId, memberUserIds.size());

        for (Number userIdNum : memberUserIds) {
            Long userId = userIdNum.longValue();
            try {
                CreateOrderRequest.OrderItemInput item = new CreateOrderRequest.OrderItemInput(
                        productId, skuId, 1, null, null, null, null
                );
                CreateOrderRequest request = new CreateOrderRequest(
                        UUID.randomUUID().toString(), List.of(item), null, null, null, null, activityId
                );
                OrderDTO order = orderService.createOrder(userId, request);
                log.info("Group buy order created: orderId={}, userId={}", order.id(), userId);
            } catch (Exception e) {
                log.error("Failed to create group buy order: userId={}, activityId={}, skuId={}",
                        userId, activityId, skuId, e);
            }
        }
    }
}
