package com.cloudmart.order.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.order.entity.Order;
import com.cloudmart.order.repository.OrderMapper;
import com.cloudmart.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class OrderTimeoutCompensationTask {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutCompensationTask.class);
    private static final int TIMEOUT_MINUTES = 15;

    private final OrderMapper orderMapper;
    private final OrderService orderService;
    private final Clock clock;

    public OrderTimeoutCompensationTask(OrderMapper orderMapper, OrderService orderService, Clock clock) {
        this.orderMapper = orderMapper;
        this.orderService = orderService;
        this.clock = clock;
    }

    @Scheduled(fixedRate = 300000)
    public void cancelTimeoutOrders() {
        LocalDateTime threshold = LocalDateTime.now(clock).minusMinutes(TIMEOUT_MINUTES);
        List<Order> timeoutOrders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getStatus, "PENDING_PAYMENT")
                        .lt(Order::getCreatedAt, threshold)
        );
        for (Order order : timeoutOrders) {
            try {
                orderService.cancelTimeoutOrder(order.getOrderNo());
                log.info("Compensation: cancelled timeout order {}", order.getOrderNo());
            } catch (Exception e) {
                log.error("Compensation: failed to cancel timeout order {}", order.getOrderNo(), e);
            }
        }
    }
}
