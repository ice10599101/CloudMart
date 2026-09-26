package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.pet.entity.PetOutboxEvent;
import com.cloudmart.pet.mq.PetEventProducer;
import com.cloudmart.pet.repository.PetOutboxEventMapper;
import com.cloudmart.pet.util.PetJsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 事务性事件发件箱（B01/B19）：业务事务内写入事件行，提交后由 {@link #dispatchPending}
 * 异步投递 MQ——业务回滚则事件行一并回滚（不产生假成功通知），发送失败按退避重试，
 * 不再依赖"事务内直接发 MQ、失败仅日志"的不可靠路径。
 *
 * <p>eventId 为确定性业务事件键（TYPE:实例），同一业务事实重复触发不产生重复事件行
 * （唯一键幂等），消费者按 eventId 二次去重（MQ at-least-once 兜底）。</p>
 */
@Component
@Slf4j
public class PetOutboxService {

    /** 退避基数：第 n 次失败后等待 2^n * 30 秒，上限 30 分钟 */
    private static final long RETRY_BACKOFF_BASE_SECONDS = 30;
    private static final long RETRY_BACKOFF_MAX_SECONDS = 1800;
    private static final int BATCH_SIZE = 100;

    private final PetOutboxEventMapper outboxMapper;
    private final PetEventProducer eventProducer;
    private final com.cloudmart.pet.config.PetMetrics metrics;
    private final org.springframework.beans.factory.ObjectProvider<com.cloudmart.pet.repository.PetDiaryEntryMapper> diaryMapperProvider;

    private com.cloudmart.pet.repository.PetDiaryEntryMapper petDiaryEntryMapper;

    public PetOutboxService(PetOutboxEventMapper outboxMapper, PetEventProducer eventProducer,
                            com.cloudmart.pet.config.PetMetrics metrics,
                            org.springframework.beans.factory.ObjectProvider<com.cloudmart.pet.repository.PetDiaryEntryMapper> diaryMapperProvider) {
        this.outboxMapper = outboxMapper;
        this.eventProducer = eventProducer;
        this.metrics = metrics;
        this.diaryMapperProvider = diaryMapperProvider;
        this.petDiaryEntryMapper = null;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setDiaryMapper(com.cloudmart.pet.repository.PetDiaryEntryMapper mapper) {
        this.petDiaryEntryMapper = mapper;
    }

    /** MQ 消息体即 PetEventProducer.PetEventMessage（B07 契约：Long 一律字符串；eventId 供消费者去重） */

    /**
     * 在当前业务事务内登记事件（业务提交后才会被发送）。同一 eventId 重复登记静默跳过
     * （幂等），禁止覆盖已排队事件。
     */
    public void record(String eventId, String eventType, Long userId, Long petId,
                       PetEventProducer.PetEventMessage message) {
        PetOutboxEvent event = new PetOutboxEvent();
        event.setEventId(eventId);
        event.setEventType(eventType);
        event.setUserId(userId);
        event.setPetId(petId);
        event.setPayload(PetJsonUtils.toJson(message));
        event.setStatus("NEW");
        event.setRetryCount(0);
        try {
            outboxMapper.insert(event);
        } catch (DuplicateKeyException duplicate) {
            log.debug("事件已登记（幂等跳过）, eventId={}", eventId);
        }
    }

    /** 提交后异步发送：每 5 秒批量推进 NEW/到期 FAILED 事件，逐条独立事务 */
    @Scheduled(fixedDelay = 5000)
    public void dispatchPending() {
        List<PetOutboxEvent> events = outboxMapper.selectList(new LambdaQueryWrapper<PetOutboxEvent>()
                .in(PetOutboxEvent::getStatus, "NEW", "FAILED")
                .and(w -> w.isNull(PetOutboxEvent::getNextRetryAt)
                        .or().le(PetOutboxEvent::getNextRetryAt, LocalDateTime.now(ZoneOffset.UTC)))
                .orderByAsc(PetOutboxEvent::getId)
                .last("LIMIT " + BATCH_SIZE));
        for (PetOutboxEvent event : events) {
            dispatchOne(event);
        }
    }

    /** 逐条推进：单条失败不拖垮批次；状态回写为单行 UPDATE，自动提交，不与业务事务互相牵连 */
    private void dispatchOne(PetOutboxEvent event) {
        PetEventProducer.PetEventMessage payload = PetJsonUtils.parse(event.getPayload(),
                new com.fasterxml.jackson.core.type.TypeReference<PetEventProducer.PetEventMessage>() {
                });
        boolean sent = eventProducer.tryPublish(event.getEventType(), payload);
        // N02：业务事实事件同步生成成长日记（eventId 复用，天然去重）
        try {
            if (petDiaryEntryMapper != null && payload.userId() != null) {
                com.cloudmart.pet.entity.PetDiaryEntry entry = new com.cloudmart.pet.entity.PetDiaryEntry();
                entry.setPetId(event.getPetId());
                entry.setUserId(Long.valueOf(payload.userId()));
                entry.setEventId(payload.eventId());
                entry.setEventType(payload.reminderType());
                entry.setOccurredAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
                entry.setSnapshot(event.getPayload());
                entry.setVisibility("OWNER_ONLY");
                petDiaryEntryMapper.insert(entry);
            }
        } catch (Exception e) {
            log.debug("日记生成幂等跳过: eventId={}", event.getEventId());
        }
        if (sent) {
            event.setStatus("SENT");
        } else {
            metrics.increment("pet_outbox_failed", "type", event.getEventType());
            int retryCount = event.getRetryCount() == null ? 1 : event.getRetryCount() + 1;
            event.setStatus("FAILED");
            event.setRetryCount(retryCount);
            long backoff = Math.min(RETRY_BACKOFF_BASE_SECONDS * (1L << Math.min(retryCount, 6)),
                    RETRY_BACKOFF_MAX_SECONDS);
            event.setNextRetryAt(LocalDateTime.now(ZoneOffset.UTC).plusSeconds(backoff));
            log.warn("事件发送失败进入退避, eventId={}, retryCount={}, backoffSeconds={}",
                    event.getEventId(), retryCount, backoff);
        }
        outboxMapper.updateById(event);
    }
}
