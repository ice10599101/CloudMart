package com.cloudmart.notification.mq;

import com.cloudmart.notification.config.RocketMQConfig;
import com.cloudmart.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = RocketMQConfig.ORDER_TOPIC,
        consumerGroup = RocketMQConfig.CG_NOTIFICATION_ORDER_STATUS,
        selectorExpression = RocketMQConfig.ORDER_TAG_STATUS_CHANGE
)
public class OrderEventConsumer implements RocketMQListener<OrderEventConsumer.OrderStatusChangeMessage> {

    private final NotificationService notificationService;

    @Override
    public void onMessage(OrderStatusChangeMessage message) {
        try {
            String title = "订单状态更新";
            String content = switch (message.newStatus()) {
                case "CANCELLED" -> "您的订单已取消";
                case "PENDING_PAYMENT" -> "您有新的待支付订单";
                case "PAID" -> "您的订单已支付成功";
                case "SHIPPED" -> "您的订单已发货";
                case "COMPLETED" -> "您的订单已完成";
                default -> "您的订单状态已更新为: " + message.newStatus();
            };
            notificationService.sendNotificationToUser(
                    message.userId(), "ORDER_STATUS", title, content,
                    message.orderId(), "ORDER"
            );
            log.info("Order status notification sent: orderId={}, status={}", message.orderId(), message.newStatus());
        } catch (Exception e) {
            log.error("Failed to send order status notification: orderId={}", message.orderId(), e);
        }
    }

    public record OrderStatusChangeMessage(
        Long orderId,
        Long userId,
        String oldStatus,
        String newStatus
    ) implements Serializable {}
}
