package com.cloudmart.common.async.outbox;

import com.cloudmart.common.async.EventEnvelope;
import com.cloudmart.common.async.mapper.OutboxEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbox 写入服务（ASYNC-01）。
 *
 * <p>{@link #record} 必须与业务写处于同一数据库事务（REQUIRED 传播）：
 * 业务提交则事件随之可见，业务回滚则事件一并回滚——杜绝
 * "消息已发、DB 回滚"与"DB 提交、消息丢失"两种断裂。</p>
 */
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxEventMapper mapper;

    public OutboxService(OutboxEventMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 在当前业务事务内登记事件（调用方必须在 @Transactional 上下文中调用）。
     * 同一 eventId 重复登记（重试路径重放）为无害操作。
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(EventEnvelope envelope) {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.setEventId(envelope.eventId());
        entity.setEventType(envelope.eventType());
        entity.setSchemaVersion(envelope.schemaVersion());
        entity.setAggregateId(envelope.aggregateId());
        entity.setAggregateVersion(envelope.aggregateVersion());
        entity.setRequestId(envelope.requestId());
        entity.setPayload(envelope.payload());
        entity.setStatus("PENDING");
        entity.setAttempts(0);
        int inserted = mapper.insertIfAbsent(entity);
        if (inserted == 0) {
            log.info("[ASYNC01] Outbox 事件已存在（幂等忽略） eventId={}", envelope.eventId());
        }
    }
}
