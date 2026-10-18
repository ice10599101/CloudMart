package com.cloudmart.wms.mq;

import com.cloudmart.common.async.EventEnvelope;
import com.cloudmart.common.async.outbox.OutboxDelivery;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

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
    private final ObjectMapper objectMapper;

    @Override
    public String destination(EventEnvelope envelope) {
        return switch (envelope.eventType()) {
            case "ORDER_SHIPPED" -> ORDER_TOPIC + ":" + ORDER_TAG_SHIPPED;
            default -> throw new IllegalArgumentException("未知的 WMS 事件类型: " + envelope.eventType());
        };
    }

    /**
     * T05：消息体与交易域其他事件一致——payload 必须是嵌套对象。
     *
     * <p>旧实现直接发送带 {@code payload} 字符串的 record，订单侧严格解析
     * （{@code requirePayload} 要求 Map）会直接拒绝，出库事件永远无法推进订单。</p>
     */
    @Override
    public void deliver(EventEnvelope envelope) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("eventId", envelope.eventId());
            body.put("eventType", envelope.eventType());
            body.put("schemaVersion", envelope.schemaVersion());
            body.put("aggregateId", envelope.aggregateId());
            body.put("aggregateVersion", envelope.aggregateVersion());
            body.put("occurredAt", envelope.occurredAt());
            body.put("requestId", envelope.requestId());
            body.put("payload", objectMapper.readValue(envelope.payload(), Map.class));

            rocketMQTemplate.syncSend(destination(envelope), MessageBuilder.withPayload(body).build());
        } catch (Exception e) {
            throw new IllegalStateException("wms outbox delivery failed: " + e.getMessage(), e);
        }
    }
}
