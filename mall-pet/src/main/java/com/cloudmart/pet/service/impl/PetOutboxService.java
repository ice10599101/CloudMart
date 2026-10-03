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
    private final com.cloudmart.pet.repository.PetMapper petMapper;

    private com.cloudmart.pet.repository.PetDiaryEntryMapper petDiaryEntryMapper;

    public PetOutboxService(PetOutboxEventMapper outboxMapper, PetEventProducer eventProducer,
                            com.cloudmart.pet.config.PetMetrics metrics,
                            org.springframework.beans.factory.ObjectProvider<com.cloudmart.pet.repository.PetDiaryEntryMapper> diaryMapperProvider,
                            com.cloudmart.pet.repository.PetMapper petMapper) {
        this.outboxMapper = outboxMapper;
        this.eventProducer = eventProducer;
        this.metrics = metrics;
        this.diaryMapperProvider = diaryMapperProvider;
        this.petMapper = petMapper;
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
     *
     * <p>R15：petId 语义为<b>事件主体宠物</b>（日记归属依据），由调用方显式传入，
     * 不再从 bizId 推断；null=无宠物主体（运维/举报等），不生成宠物日记。</p>
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
            // R15：单事件全流程隔离——坏 payload/单条异常不阻断批次（原实现坏 JSON 可中断整批）
            try {
                dispatchOne(event);
            } catch (Exception e) {
                log.error("发件箱单事件推进异常, eventId={}", event.getEventId(), e);
                markDead(event, String.valueOf(e.getMessage()));
            }
        }
    }

    /** R15：不可恢复事件进 DEAD（后台可查/同事件重放），不阻塞后续事件 */
    private void markDead(PetOutboxEvent event, String error) {
        try {
            outboxMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PetOutboxEvent>()
                    .set(PetOutboxEvent::getStatus, "DEAD")
                    .set(PetOutboxEvent::getNextRetryAt, null)
                    .eq(PetOutboxEvent::getId, event.getId())
                    .eq(PetOutboxEvent::getStatus, event.getStatus()));
            metrics.increment("pet_outbox_dead", "type", String.valueOf(event.getEventType()));
            log.warn("发件箱事件置 DEAD: eventId={}, error={}", event.getEventId(), error);
        } catch (Exception e) {
            log.error("发件箱 DEAD 标记失败（下轮重试）, eventId={}", event.getEventId(), e);
        }
    }

    /** 逐条推进：单条失败不拖垮批次；状态回写为单行 UPDATE，自动提交，不与业务事务互相牵连 */
    private void dispatchOne(PetOutboxEvent event) {
        PetEventProducer.PetEventMessage payload = PetJsonUtils.parse(event.getPayload(),
                new com.fasterxml.jackson.core.type.TypeReference<PetEventProducer.PetEventMessage>() {
                });
        if (payload == null || payload.eventId() == null) {
            // R15：坏 payload 不可解析——置 DEAD 隔离（原实现抛出中断本批，坏消息阻塞后续全部事件）
            throw new IllegalStateException("payload 不可解析（坏消息）");
        }
        // 需求：用户自定义主人称呼——事件类通知（挑战/留言/赛季奖励等）在投递前统一替换
        // 文案中的「主人」为收件人设置的称呼（宠物口吻漏斗之外的收口点，覆盖全部 PET 事件）。
        // 查询失败/无宠时 Fail-Open 保留原文案。
        if (payload.userId() != null) {
            try {
                com.cloudmart.pet.entity.Pet addressPet = petMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.cloudmart.pet.entity.Pet>()
                                .eq(com.cloudmart.pet.entity.Pet::getUserId, Long.valueOf(payload.userId()))
                                .eq(com.cloudmart.pet.entity.Pet::getIsActive, true)
                                .last("LIMIT 1"));
                if (addressPet != null) {
                    String address = PetServiceImpl.ownerTitleOf(addressPet);
                    if (!"主人".equals(address)) {
                        payload = new PetEventProducer.PetEventMessage(payload.eventId(), payload.userId(),
                                payload.reminderType(),
                                payload.title() == null ? null : payload.title().replace("主人", address),
                                payload.content() == null ? null : payload.content().replace("主人", address),
                                payload.bizId(), payload.bizType());
                    }
                }
            } catch (Exception replaceError) {
                // 称呼替换为展示增强：失败按原文案投递（不阻断事件投递主链路）
            }
        }
        boolean sent = eventProducer.tryPublish(event.getEventType(), payload);
        // N02/R15：业务事实事件同步生成成长日记——归属=事件主体宠物（显式登记，不从 bizId 推断）；
        // 无宠物主体的事件不生成日记。日记失败时整体保持 FAILED 重试（MQ 消费者按 eventId 去重
        // 保证重发安全；日记 eventId 唯一键保证重建不重复）——不再"MQ 成功即 SENT、日记永久缺失"。
        boolean diaryOk = true;
        if (petDiaryEntryMapper != null && payload.userId() != null && event.getPetId() != null) {
            try {
                com.cloudmart.pet.entity.PetDiaryEntry entry = new com.cloudmart.pet.entity.PetDiaryEntry();
                entry.setPetId(event.getPetId());
                entry.setUserId(Long.valueOf(payload.userId()));
                entry.setEventId(payload.eventId());
                entry.setEventType(payload.reminderType());
                entry.setOccurredAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
                entry.setSnapshot(event.getPayload());
                entry.setVisibility("OWNER_ONLY");
                petDiaryEntryMapper.insert(entry);
            } catch (DuplicateKeyException duplicate) {
                // 日记已存在（重放）：视为成功
            } catch (Exception e) {
                diaryOk = false;
                log.warn("日记生成失败（事件保持可重试，不静默丢失）: eventId={}", event.getEventId(), e);
            }
        }
        if (sent && !diaryOk) {
            // MQ 已发出但日记未落：按 FAILED 退避重试——重发由消费者 eventId 去重收敛，日记补建
            sent = false;
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
