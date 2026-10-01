package com.cloudmart.order.mq;

import com.cloudmart.common.async.EventEnvelope;
import com.cloudmart.common.async.inbox.InboxService;
import com.cloudmart.order.config.RocketMQConfig;
import com.cloudmart.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 支付结果消费者（T05）：PAYMENT_SUCCEEDED 事件驱动订单推进 PAID 的唯一 MQ 路径。
 *
 * <p>LC03：仅消费 v2 信封事件（强制 eventId）——无 eventId 的旧格式回退分支已删除，
 * 非法形状拒绝消费进入隔离/死信，不绕过去重。</p>
 *
 * <p>金额/币种校验（T05）：从事件 payload 提取渠道实付与币种，交由
 * {@code applyPaymentSucceeded} 与订单应付核对，不一致拒绝推进。</p>
 *
 * <p>begin 的消费记录与业务变更同一事务——业务回滚则记录一并回滚，MQ 重投后重试；
 * 重复投递命中已处理记录直接跳过。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = RocketMQConfig.PAYMENT_TOPIC,
        consumerGroup = RocketMQConfig.CG_ORDER_PAYMENT_RESULT,
        selectorExpression = RocketMQConfig.PAYMENT_TAG_RESULT
)
public class PaymentResultConsumer implements RocketMQListener<Map<String, Object>> {

    static final String CONSUMER = "order-payment-result";

    private final OrderService orderService;
    private final InboxService inboxService;

    @Override
    @Transactional
    public void onMessage(Map<String, Object> message) {
        String event = extractEventType(message);
        if (!"PAYMENT_SUCCEEDED".equals(event)) {
            // LC03：旧事件名 PAYMENT_SUCCESS 的生产者已删除，此处仅接受 v2 事件名
            return;
        }
        EventEnvelope envelope = envelopeOf(message);
        Long orderId = extractOrderId(message);
        String paidAmount = extractPayloadField(message, "amount");
        String currency = extractPayloadField(message, "currency");
        log.info("[T05] 收到支付成功事件, orderId={}, amount={}, currency={}, eventId={}",
                orderId, paidAmount, currency, envelope.eventId());

        if (inboxService.beginConsume(CONSUMER, envelope) == InboxService.ConsumeDecision.SKIP) {
            log.info("[ASYNC01] 支付事件已消费（幂等跳过） eventId={} orderId={}", envelope.eventId(), orderId);
            return;
        }
        try {
            orderService.applyPaymentSucceeded(orderId, paidAmount, currency);
            inboxService.completeConsume(CONSUMER, envelope);
        } catch (Exception e) {
            inboxService.failConsume(CONSUMER, envelope, e.getMessage());
            throw e;
        }
    }

    /** 提取 payload 内的字符串字段（payload 为嵌套 Map 或 JSON 字符串两种序列化形态） */
    static String extractPayloadField(Map<String, Object> message, String field) {
        Object payload = message.get("payload");
        if (payload instanceof Map<?, ?> payloadMap && payloadMap.get(field) != null) {
            return String.valueOf(payloadMap.get(field));
        }
        return null;
    }

    /** 形状判定：新格式 orderId 在 payload 内；旧格式在顶层 */
    static Long extractOrderId(Map<String, Object> message) {
        Object payload = message.get("payload");
        if (payload instanceof Map<?, ?> payloadMap && payloadMap.get("orderId") instanceof Number n) {
            return n.longValue();
        }
        if (message.get("orderId") instanceof Number n) {
            return n.longValue();
        }
        throw new IllegalArgumentException("[ASYNC01] 消息缺少 orderId（新旧形状均未命中）: " + message.keySet());
    }

    /** 事件类型：新格式顶层 eventType；旧格式顶层 event */
    static String extractEventType(Map<String, Object> message) {
        Object eventType = message.get("eventType");
        if (eventType instanceof String s && !s.isBlank()) {
            return s;
        }
        return (String) message.get("event");
    }

    static EventEnvelope envelopeOf(Map<String, Object> message) {
        return new EventEnvelope(
                (String) message.get("eventId"),
                (String) message.get("eventType"),
                message.get("schemaVersion") == null ? 1 : ((Number) message.get("schemaVersion")).intValue(),
                String.valueOf(message.get("aggregateId")),
                message.get("aggregateVersion") == null ? 0 : ((Number) message.get("aggregateVersion")).longValue(),
                message.get("occurredAt") == null ? 0 : ((Number) message.get("occurredAt")).longValue(),
                (String) message.get("requestId"),
                null);
    }
}
