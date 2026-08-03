package com.cloudmart.order.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.order.entity.Order;
import com.cloudmart.order.repository.OrderMapper;
import com.cloudmart.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutScheduler {

    private static final int TIMEOUT_MINUTES = 15;
    private static final int BATCH_SIZE = 100;

    private final OrderMapper orderMapper;
    private final OrderService orderService;

    @Scheduled(fixedDelay = 60_000)
    public void cancelTimeoutOrders() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES);

        List<Order> timeoutOrders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getStatus, "PENDING_PAYMENT")
                        .lt(Order::getCreatedAt, threshold)
                        .last("LIMIT " + BATCH_SIZE)
        );

        if (timeoutOrders.isEmpty()) {
            return;
        }

        log.info("发现{}个超时未支付订单，开始自动取消", timeoutOrders.size());

        for (Order order : timeoutOrders) {
            try {
                orderService.notifyOrderCancel(order.getId());
                log.info("超时订单已取消, orderId={}", order.getId());
            } catch (Exception e) {
                log.error("超时订单取消失败, orderId={}: {}", order.getId(), e.getMessage());
            }
        }
    }
}
