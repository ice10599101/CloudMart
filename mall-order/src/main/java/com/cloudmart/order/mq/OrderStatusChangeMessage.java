package com.cloudmart.order.mq;

import java.io.Serializable;

public record OrderStatusChangeMessage(
    Long orderId,
    Long userId,
    String oldStatus,
    String newStatus
) implements Serializable {
}
