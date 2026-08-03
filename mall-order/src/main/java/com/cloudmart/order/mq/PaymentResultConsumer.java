package com.cloudmart.order.mq;

import com.cloudmart.order.config.RocketMQConfig;
import com.cloudmart.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = RocketMQConfig.PAYMENT_TOPIC,
        consumerGroup = RocketMQConfig.CG_ORDER_PAYMENT_RESULT,
        selectorExpression = RocketMQConfig.PAYMENT_TAG_RESULT
)
public class PaymentResultConsumer implements RocketMQListener<Map<String, Object>> {

    private final OrderService orderService;
    private final OrderEventProducer orderEventProducer;

    @Override
    public void onMessage(Map<String, Object> message) {
        String event = (String) message.get("event");
        Long orderId = ((Number) message.get("orderId")).longValue();
        log.info("收到支付结果消息, orderId={}, event={}", orderId, event);

        if ("PAYMENT_SUCCESS".equals(event)) {
            orderService.markOrderPaid(orderId);
            orderEventProducer.sendOrderPaid(orderId);
        }
    }
}
