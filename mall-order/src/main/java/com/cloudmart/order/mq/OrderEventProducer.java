package com.cloudmart.order.mq;

import com.cloudmart.order.config.RocketMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventProducer {

    private final RocketMQTemplate rocketMQTemplate;

    public boolean sendOrderStatusChange(OrderStatusChangeMessage message) {
        try {
            String destination = RocketMQConfig.ORDER_TOPIC + ":" + RocketMQConfig.ORDER_TAG_STATUS_CHANGE;
            rocketMQTemplate.syncSend(destination, message);
            log.info("Order status change message sent: orderId={}, status={}", message.orderId(), message.newStatus());
            return true;
        } catch (Exception e) {
            log.error("Failed to send order status change message: orderId={}", message.orderId(), e);
            return false;
        }
    }

    /**
     * 发送订单超时检查延时消息。
     *
     * <p>原 RabbitMQ 实现为 TTL=600s + DLX 转发到 order.timeout queue；
     * 改用 RocketMQ delayLevel=16（10min）直接投递到 {@code order-events:timeout-check}，
     * 到期后由 {@link OrderTimeoutConsumer} 消费。
     */
    public boolean sendOrderTimeoutCheck(String orderNo) {
        try {
            String destination = RocketMQConfig.ORDER_TOPIC + ":" + RocketMQConfig.ORDER_TAG_TIMEOUT_CHECK;
            rocketMQTemplate.syncSend(
                    destination,
                    MessageBuilder.withPayload(orderNo).build(),
                    3000,
                    RocketMQConfig.DELAY_LEVEL_ORDER_TIMEOUT
            );
            log.info("Order timeout check message sent: orderNo={}", orderNo);
            return true;
        } catch (Exception e) {
            log.error("Failed to send order timeout check message: orderNo={}", orderNo, e);
            return false;
        }
    }

    public boolean sendOrderPaid(Long orderId) {
        try {
            String destination = RocketMQConfig.ORDER_TOPIC + ":" + RocketMQConfig.ORDER_TAG_PAID;
            rocketMQTemplate.syncSend(destination, Map.of("orderId", orderId));
            log.info("Order paid message sent: orderId={}", orderId);
            return true;
        } catch (Exception e) {
            log.error("Failed to send order paid message: orderId={}", orderId, e);
            return false;
        }
    }
}
