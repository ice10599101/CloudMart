package com.cloudmart.order.mq;

import com.cloudmart.common.async.EventEnvelope;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * C20：交易域 MQ 事件统一严格解析。所有交易事件（支付/成团/发运）只接受 Outbox
 * 生产的 v2 信封：eventId/eventType/schemaVersion 必填、业务字段在嵌套 payload 内。
 * 缺字段或旧顶层形状一律 {@link IllegalArgumentException} 拒绝（由 MQ 重试/死信处置），
 * 不再提供静默兜底。
 */
final class OrderEventMessages {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OrderEventMessages() {
    }

    /** 严格解析信封头；可选字段（aggregateId/版本/时间/requestId）缺失按零值处理，必填三件套缺失即拒绝。 */
    static EventEnvelope requireEnvelope(Map<String, Object> message) {
        Object eventId = message.get("eventId");
        Object eventType = message.get("eventType");
        Object schemaVersion = message.get("schemaVersion");
        if (!(eventId instanceof String id) || id.isBlank()
                || !(eventType instanceof String type) || type.isBlank()
                || !(schemaVersion instanceof Number version)) {
            throw new IllegalArgumentException(
                    "交易事件信封缺少 eventId/eventType/schemaVersion（旧形状拒绝消费）: " + message.keySet());
        }
        return new EventEnvelope(id, type, version.intValue(),
                message.get("aggregateId") == null ? null : String.valueOf(message.get("aggregateId")),
                message.get("aggregateVersion") instanceof Number aggregateVersion
                        ? aggregateVersion.longValue() : 0,
                message.get("occurredAt") instanceof Number occurredAt ? occurredAt.longValue() : 0,
                message.get("requestId") instanceof String requestId ? requestId : null,
                null);
    }

    /** 取出嵌套 payload Map（缺失/非对象即拒绝）。 */
    @SuppressWarnings("unchecked")
    static Map<String, Object> requirePayload(Map<String, Object> message) {
        if (message.get("payload") instanceof Map<?, ?> payload) {
            return (Map<String, Object>) payload;
        }
        throw new IllegalArgumentException("交易事件缺少 payload 对象（旧形状拒绝消费）: " + message.keySet());
    }

    /** 把 payload 强类型转换为契约记录；字段缺失/类型不符由消费侧显式判定。 */
    static <T> T payload(Map<String, Object> message, Class<T> payloadType) {
        return MAPPER.convertValue(requirePayload(message), payloadType);
    }
}
