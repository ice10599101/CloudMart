package com.cloudmart.common.async.inbox;

import com.cloudmart.common.async.EventEnvelope;
import com.cloudmart.common.async.mapper.InboxRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inbox 幂等消费服务（ASYNC-01）。
 *
 * <p>消费方模板（必须在消费方自身的 @Transactional 事务内调用 begin）：</p>
 * <pre>{@code
 * @Transactional
 * public void onMessage(EventEnvelope event) {
 *     if (inbox.beginConsume("order-payment-result", event) == ConsumeDecision.SKIP) {
 *         return; // 已消费/他实例处理中：容忍 MQ 重发
 *     }
 *     try {
 *         // 业务变更（与 inbox 行同一事务）
 *         inbox.completeConsume("order-payment-result", event);
 *     } catch (Exception e) {
 *         inbox.failConsume("order-payment-result", event, e.getMessage());
 *         throw e; // 抛出 → 事务回滚（inbox 行一并回滚）→ MQ 重投
 *     }
 * }
 * }</pre>
 *
 * <p>关键性质：begin 的 INSERT 与业务写同事务——业务回滚则"已消费"标记一并回滚，
 * 不存在"处理前永久标记已消费"；complete 在业务变更之后调用，保证
 * "业务生效 ⇒ 标记生效"；重复投递必然命中已存在行而被跳过。</p>
 */
public class InboxService {

    private static final Logger log = LoggerFactory.getLogger(InboxService.class);

    public enum ConsumeDecision { PROCEED, SKIP }

    private final InboxRecordMapper mapper;
    private final int processingLeaseSeconds;

    public InboxService(InboxRecordMapper mapper, int processingLeaseSeconds) {
        this.mapper = mapper;
        this.processingLeaseSeconds = processingLeaseSeconds;
    }

    /**
     * 抢占消费权（参与当前事务）。
     *
     * @return PROCEED 首次消费或失败重试；SKIP 已处理完成或他实例处理中
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public ConsumeDecision beginConsume(String consumer, EventEnvelope event) {
        int inserted = mapper.insertProcessing(consumer, event.eventId(), event.eventType(), event.aggregateId());
        if (inserted == 1) {
            return ConsumeDecision.PROCEED;
        }
        InboxRecordEntity existing = mapper.find(consumer, event.eventId());
        if (existing == null) {
            // 并发下另一事务刚插入并提交：本次按跳过处理（MQ 会重投兜底）
            return ConsumeDecision.SKIP;
        }
        if ("PROCESSED".equals(existing.getStatus())) {
            return ConsumeDecision.SKIP;
        }
        int tookOver = mapper.takeOver(consumer, event.eventId(), processingLeaseSeconds);
        if (tookOver == 1) {
            log.info("[ASYNC01] Inbox 接管失败事件重试 consumer={} eventId={} attempts={}",
                    consumer, event.eventId(), existing.getAttempts() + 1);
            return ConsumeDecision.PROCEED;
        }
        return ConsumeDecision.SKIP;
    }

    /** 业务变更成功后标记完成（必须与业务变更同事务调用） */
    @Transactional(propagation = Propagation.REQUIRED)
    public void completeConsume(String consumer, EventEnvelope event) {
        mapper.markProcessed(consumer, event.eventId());
    }

    /** 业务变更失败时记录错误；调用方应继续抛出以回滚事务（含本行变更） */
    @Transactional(propagation = Propagation.REQUIRED)
    public void failConsume(String consumer, EventEnvelope event, String error) {
        String sanitized = error == null ? "unknown error" : error;
        if (sanitized.length() > 1000) {
            sanitized = sanitized.substring(0, 1000);
        }
        mapper.markFailed(consumer, event.eventId(), sanitized);
    }
}
