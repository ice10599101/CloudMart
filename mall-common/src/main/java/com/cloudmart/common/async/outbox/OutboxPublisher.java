package com.cloudmart.common.async.outbox;

import com.cloudmart.common.async.EventEnvelope;
import com.cloudmart.common.async.mapper.OutboxEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

/**
 * Outbox 后台投递器（ASYNC-01）：批量领取待投递事件并经 {@link OutboxDelivery} 发送。
 *
 * <p>语义：</p>
 * <ul>
 *   <li>单语句抢占 + 实例租约：多实例并发安全，实例宕机后其未完成行被其他实例接管；</li>
 *   <li>投递成功标记 SENT；失败按指数退避重排，超限转 DEAD_LETTER 并告警；</li>
 *   <li>"已发送但未标记 SENT"的重发由消费端 Inbox 幂等容忍。</li>
 * </ul>
 */
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventMapper mapper;
    private final OutboxDelivery delivery;
    private final OutboxRetryPolicy retryPolicy;
    private final int batchSize;
    private final int leaseSeconds;
    private final String workerId;

    public OutboxPublisher(OutboxEventMapper mapper, OutboxDelivery delivery,
                           OutboxRetryPolicy retryPolicy, int batchSize, int leaseSeconds) {
        this.mapper = mapper;
        this.delivery = delivery;
        this.retryPolicy = retryPolicy;
        this.batchSize = batchSize;
        this.leaseSeconds = leaseSeconds;
        this.workerId = java.util.UUID.randomUUID().toString();
    }

    /**
     * ASYNC-01：后台投递轮询——可配置 fixedDelay（默认 1 秒）。
     * 此前方法无 @Scheduled 且无生产调用入口，Outbox 事件永远停在 PENDING。
     */
    @Scheduled(fixedDelayString = "${cloudmart.async.outbox.publish-delay-ms:1000}")
    public void publishPending() {
        try {
            int claimed = mapper.claimBatch(workerId, leaseSeconds, batchSize);
            if (claimed == 0) {
                return;
            }
            List<OutboxEventEntity> batch = mapper.selectClaimed(workerId, leaseSeconds, batchSize);
            for (OutboxEventEntity event : batch) {
                deliverOne(event);
            }
        } catch (Exception e) {
            log.error("[ASYNC01] Outbox 投递轮次异常: {}", e.getMessage());
        }
    }

    private void deliverOne(OutboxEventEntity event) {
        EventEnvelope envelope = new EventEnvelope(
                event.getEventId(), event.getEventType(),
                event.getSchemaVersion() == null ? 1 : event.getSchemaVersion(),
                event.getAggregateId(),
                event.getAggregateVersion() == null ? 0 : event.getAggregateVersion(),
                event.getCreatedAt() == null ? System.currentTimeMillis()
                        : event.getCreatedAt().toInstant(java.time.ZoneOffset.UTC).toEpochMilli(),
                event.getRequestId(),
                event.getPayload());
        try {
            delivery.deliver(envelope);
            mapper.markSent(event.getId());
            log.info("[ASYNC01] 事件投递成功 type={} eventId={} aggregate={}",
                    envelope.eventType(), envelope.eventId(), envelope.aggregateId());
        } catch (Exception e) {
            long backoff = retryPolicy.nextBackoffMillis(
                    event.getAttempts() == null ? 0 : event.getAttempts());
            int updated = mapper.markFailure(event.getId(), retryPolicy.maxAttempts(), backoff,
                    sanitize(e.getMessage()));
            if (updated > 0) {
                log.warn("[ASYNC01] 事件投递失败 type={} eventId={} attempts={} backoff={}ms error={}",
                        envelope.eventType(), envelope.eventId(),
                        (event.getAttempts() == null ? 0 : event.getAttempts()) + 1, backoff,
                        sanitize(e.getMessage()));
            }
        }
    }

    /** 错误信息脱敏截断（工作台可见，禁止携带敏感数据） */
    static String sanitize(String message) {
        if (message == null) {
            return "unknown error";
        }
        String cleaned = message.replaceAll("(?i)(password|token|secret|authorization)=[^,&\\s]+", "$1=***");
        return cleaned.length() > 1000 ? cleaned.substring(0, 1000) : cleaned;
    }
}
