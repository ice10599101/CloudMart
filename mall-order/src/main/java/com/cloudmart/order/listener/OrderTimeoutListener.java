package com.cloudmart.order.listener;

import com.cloudmart.order.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderTimeoutListener extends KeyExpirationEventMessageListener {

    private static final String ORDER_TIMEOUT_KEY_PREFIX = "order:timeout:";

    private final OrderService orderService;

    public OrderTimeoutListener(RedisMessageListenerContainer container, OrderService orderService) {
        super(container);
        this.orderService = orderService;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = message.toString();
        if (expiredKey.startsWith(ORDER_TIMEOUT_KEY_PREFIX)) {
            String orderIdStr = expiredKey.substring(ORDER_TIMEOUT_KEY_PREFIX.length());
            try {
                Long orderId = Long.parseLong(orderIdStr);
                log.info("订单超时自动取消, orderId={}", orderId);
                orderService.notifyOrderCancel(orderId);
            } catch (NumberFormatException e) {
                log.warn("解析超时订单ID失败: {}", orderIdStr);
            } catch (Exception e) {
                log.error("超时取消订单失败, orderId={}: {}", orderIdStr, e.getMessage());
            }
        }
    }
}
