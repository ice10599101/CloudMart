package com.cloudmart.order.mq;

import com.cloudmart.order.config.RocketMQConfig;
import com.cloudmart.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = RocketMQConfig.ORDER_TOPIC,
        consumerGroup = RocketMQConfig.CG_ORDER_TIMEOUT,
        selectorExpression = RocketMQConfig.ORDER_TAG_TIMEOUT_CHECK
)
public class OrderTimeoutConsumer implements RocketMQListener<String> {

    private final OrderService orderService;

    @Override
    public void onMessage(String orderNo) {
        log.info("Received order timeout check message for order: {}", orderNo);
        try {
            orderService.cancelTimeoutOrder(orderNo);
        } catch (Exception e) {
            log.error("Failed to cancel timeout order: {}", orderNo, e);
        }
    }
}
