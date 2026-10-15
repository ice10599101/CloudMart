package com.cloudmart.software.mq;

import com.cloudmart.common.async.EventEnvelope;
import com.cloudmart.common.async.outbox.OutboxDelivery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * 软件事件 Outbox 投递适配器（方案 9.4）：把软件域事件发往 software-events topic。
 *
 * <p>同本地事务写 outbox_event，由公共 OutboxPublisher 租约调度投递；失败抛异常
 * 由 Outbox 统一退避重试，消费端（mall-notification）按 eventId + recipientId 幂等。
 * 事件体只携带业务标识与状态，不携带下载链接、提取码或正文。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SoftwareOutboxDelivery implements OutboxDelivery {

    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public String destination(EventEnvelope envelope) {
        return SoftwareEvents.TOPIC + ":" + tagFor(envelope.eventType());
    }

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
            throw new IllegalStateException("software outbox delivery failed: " + e.getMessage(), e);
        }
    }

    private String tagFor(String eventType) {
        return switch (eventType) {
            case "SOFTWARE_REVIEW_APPROVED" -> SoftwareEvents.TAG_REVIEW_APPROVED;
            case "SOFTWARE_REVIEW_REJECTED" -> SoftwareEvents.TAG_REVIEW_REJECTED;
            case "SOFTWARE_COMMENT_VISIBLE" -> SoftwareEvents.TAG_COMMENT_VISIBLE;
            case "SOFTWARE_REPLY_VISIBLE" -> SoftwareEvents.TAG_REPLY_VISIBLE;
            case "SOFTWARE_SOURCE_REPORTED" -> SoftwareEvents.TAG_SOURCE_REPORTED;
            case "SOFTWARE_RELEASE_PUBLISHED" -> SoftwareEvents.TAG_RELEASE_PUBLISHED;
            default -> throw new IllegalArgumentException("未知的软件事件类型: " + eventType);
        };
    }
}
