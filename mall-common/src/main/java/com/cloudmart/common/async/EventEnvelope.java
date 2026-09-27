package com.cloudmart.common.async;

/**
 * 交易事件协议（ASYNC-01）：跨服务事件的统一信封。
 *
 * <p>字段约定：</p>
 * <ul>
 *   <li>{@code eventId}：全局唯一（UUID），Outbox 唯一键 / Inbox 消费幂等键；</li>
 *   <li>{@code eventType}：机器可读的事件类型（如 PAYMENT_SUCCESS）；</li>
 *   <li>{@code schemaVersion}：载荷结构版本，消费方按版本兼容解析；</li>
 *   <li>{@code aggregateId}/{@code aggregateVersion}：聚合根与版本——同聚合事件按
 *       版本去重与条件更新，旧事件忽略并留审计，缺前置事件触发回查；</li>
 *   <li>{@code occurredAt}：业务发生时间（epoch 毫秒）；</li>
 *   <li>{@code requestId}：发起请求的追踪 ID（可空）；</li>
 *   <li>{@code payload}：载荷 JSON 字符串（由调用方序列化，禁止裸 Map 随意转型）。</li>
 * </ul>
 */
public record EventEnvelope(
        String eventId,
        String eventType,
        int schemaVersion,
        String aggregateId,
        long aggregateVersion,
        long occurredAt,
        String requestId,
        String payload) {

    public static EventEnvelope of(String eventType, int schemaVersion, String aggregateId,
                                   long aggregateVersion, String requestId, String payload) {
        return new EventEnvelope(
                java.util.UUID.randomUUID().toString(),
                eventType,
                schemaVersion,
                aggregateId,
                aggregateVersion,
                System.currentTimeMillis(),
                requestId,
                payload);
    }
}
