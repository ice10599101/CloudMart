package com.cloudmart.notification.mq;

import com.cloudmart.notification.config.RocketMQConfig;
import com.cloudmart.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;


/**
 * 订单状态变更通知消费者（N01）。
 *
 * <p>修复：原实现 catch 后仅记日志——消费失败被 ACK，通知永久丢失。
 * 现在：eventId 唯一去重（notifications.uk_notification_event）+ 失败重抛
 * 触发 MQ 重试；事件信封缺 eventId（旧形状）拒绝消费（LC03）。</p>
 */
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = RocketMQConfig.ORDER_TOPIC,
        consumerGroup = RocketMQConfig.CG_NOTIFICATION_ORDER_STATUS,
        selectorExpression = RocketMQConfig.ORDER_TAG_STATUS_CHANGE
)
public class OrderEventConsumer implements RocketMQListener<Map<String, Object>> {

    private final NotificationService notificationService;

    @Override
    public void onMessage(Map<String, Object> message) {
        String eventId = (String) message.get("eventId");
        if (eventId == null || eventId.isBlank()) {
            // LC03：生产者全经 Outbox 信封——缺 eventId 的旧形状拒绝，不绕过幂等
            log.warn("[N01] 订单事件缺少 eventId（旧形状），拒绝消费");
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) message.get("payload");
        if (payload == null) {
            log.warn("[N01] 订单事件缺少 payload, eventId={}", eventId);
            return;
        }
        Long orderId = ((Number) payload.get("orderId")).longValue();
        Long userId = ((Number) payload.get("userId")).longValue();
        Object newStatusObj = payload.get("newStatus");
        String newStatus = newStatusObj == null ? "" : String.valueOf(newStatusObj);

        String title = "订单状态更新";
        String content = switch (newStatus) {
            case "CANCELLED" -> "您的订单已取消";
            case "PENDING_PAYMENT" -> "您有新的待支付订单";
            case "PAID" -> "您的订单已支付成功";
            case "SHIPPED" -> "您的订单已发货";
            case "COMPLETED" -> "您的订单已完成";
            default -> "您的订单状态已更新为: " + newStatus;
        };

        // N01：失败必须重抛触发 MQ 重试（吞异常 = 通知丢失且被 ACK）
        notificationService.sendOrderEventNotification(userId, eventId, title, content, orderId);
        log.info("Order status notification sent: orderId={}, status={}, eventId={}",
                orderId, newStatus, eventId);
    }
}
