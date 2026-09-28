package com.cloudmart.wms.mq;

import com.cloudmart.common.async.EventEnvelope;
import com.cloudmart.common.async.outbox.OutboxDelivery;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * WMS 事件 Outbox 投递适配器（WMS-01）：把 ORDER_SHIPPED 发往 order-events:shipped，
 * 订单服务消费后推进订单 SHIPPED——"仅点发货但没有包裹"的管理端直发路径被事件闭环取代。
 */
@Component
@RequiredArgsConstructor
public class WmsOutboxDelivery implements OutboxDelivery {

    public static final String ORDER_TOPIC = "order-events";
    public static final String ORDER_TAG_SHIPPED = "shipped";

    private final RocketMQTemplate rocketMQTemplate;

    @Override
    public String destination(EventEnvelope envelope) {
        return switch (envelope.eventType()) {
            case "ORDER_SHIPPED" -> ORDER_TOPIC + ":" + ORDER_TAG_SHIPPED;
            default -> throw new IllegalArgumentException("未知的 WMS 事件类型: " + envelope.eventType());
        };
    }

    @Override
    public void deliver(EventEnvelope envelope) {
        try {
            rocketMQTemplate.syncSend(destination(envelope),
                    MessageBuilder.withPayload(envelope).build());
        } catch (Exception e) {
            throw new IllegalStateException("wms outbox delivery failed: " + e.getMessage(), e);
        }
    }
}
