package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.wish.config.RocketMQConfig;
import com.cloudmart.wish.entity.WishOutboxEvent;
import com.cloudmart.wish.repository.WishOutboxMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 事务性发件箱：发布与中继（B13，任务书 §6.3）。
 *
 * <p>发布：{@link #publish} 在调用方事务内插入 PENDING 事件行——业务事实与事件原子提交；
 * 主业务不直接发送 MQ，broker 不可用不影响本地事务成功。</p>
 *
 * <p>中继：{@link #relayDueEvents} 定时领取到期 PENDING 事件（条件 UPDATE 抢租约，
 * 多实例安全），按退避 1s/5s/30s/2min/10min 重试，超过 10 次进入 DEAD（告警由
 * 监控按 DEAD 计数触发）；投递成功置 PUBLISHED 并记录 published_at。
 * 同一 eventId 重试不变化——消费端按 eventId 去重。</p>
 */
@Service
@Slf4j
public class WishOutboxService {

    /** 事件退避序列（秒）：1s/5s/30s/2min/10min，之后按 10min 循环直至 DEAD */
    static final long[] BACKOFF_SECONDS = {1, 5, 30, 120, 600};
    static final int MAX_ATTEMPTS = 10;
    private static final int RELAY_BATCH = 100;
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final WishOutboxMapper outboxMapper;
    private final RocketMQTemplate rocketMQTemplate;
    private final String instanceId;

    public WishOutboxService(WishOutboxMapper outboxMapper, RocketMQTemplate rocketMQTemplate,
                             @Value("${wish.outbox.instance-id:${HOSTNAME:wish-outbox-default}}") String instanceId) {
        this.outboxMapper = outboxMapper;
        this.rocketMQTemplate = rocketMQTemplate;
        this.instanceId = instanceId;
    }

    /**
     * 在当前事务内登记一条事件（不直接发送）。payload 只允许最小 ID/版本/展示字段。
     */
    public void publish(String aggregateType, Long aggregateId, long aggregateVersion,
                        String eventType, Map<String, Object> payload) {
        WishOutboxEvent event = new WishOutboxEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setAggregateVersion(aggregateVersion);
        event.setEventType(eventType);
        event.setPayload(toJson(payload));
        event.setStatus("PENDING");
        event.setAttempts(0);
        event.setNextAttemptAt(LocalDateTime.now(ZoneId.of("UTC")));
        event.setCreatedAt(LocalDateTime.now(ZoneId.of("UTC")));
        outboxMapper.insert(event);
    }

    /**
     * 中继到期事件：每 2 秒一轮，条件 UPDATE 抢租约（多实例互斥），逐条投递。
     */
    @Scheduled(fixedDelay = 2000)
    public void relayDueEvents() {
        List<WishOutboxEvent> due = outboxMapper.selectList(new LambdaQueryWrapper<WishOutboxEvent>()
                .eq(WishOutboxEvent::getStatus, "PENDING")
                .lt(WishOutboxEvent::getNextAttemptAt, LocalDateTime.now(ZoneId.of("UTC")))
                .orderByAsc(WishOutboxEvent::getCreatedAt).orderByAsc(WishOutboxEvent::getEventId)
                .last("LIMIT " + RELAY_BATCH));
        for (WishOutboxEvent event : due) {
            if (tryLease(event)) {
                deliver(event);
            }
        }
    }

    /** 条件 UPDATE 抢租约：只有占用成功的实例投递（fencing 语义）。 */
    private boolean tryLease(WishOutboxEvent event) {
        LocalDateTime leaseUntil = LocalDateTime.now(ZoneId.of("UTC")).plusSeconds(30);
        int claimed = outboxMapper.update(null, new LambdaUpdateWrapper<WishOutboxEvent>()
                .set(WishOutboxEvent::getLeaseOwner, instanceId)
                .set(WishOutboxEvent::getLeaseUntil, leaseUntil)
                .eq(WishOutboxEvent::getEventId, event.getEventId())
                .eq(WishOutboxEvent::getStatus, "PENDING")
                .and(w -> w.isNull(WishOutboxEvent::getLeaseUntil)
                        .or().lt(WishOutboxEvent::getLeaseUntil, LocalDateTime.now(ZoneId.of("UTC")))));
        return claimed == 1;
    }

    private void deliver(WishOutboxEvent event) {
        String destination = RocketMQConfig.WISH_TOPIC + ":" + tagOf(event.getEventType());
        try {
            rocketMQTemplate.syncSend(destination, event.getPayload());
            markPublished(event);
        } catch (Exception ex) {
            markRetryOrDead(event, ex);
        }
    }

    private void markPublished(WishOutboxEvent event) {
        outboxMapper.update(null, new LambdaUpdateWrapper<WishOutboxEvent>()
                .set(WishOutboxEvent::getStatus, "PUBLISHED")
                .set(WishOutboxEvent::getPublishedAt, LocalDateTime.now(ZoneId.of("UTC")))
                .eq(WishOutboxEvent::getEventId, event.getEventId())
                .eq(WishOutboxEvent::getStatus, "PENDING"));
    }

    private void markRetryOrDead(WishOutboxEvent event, Exception cause) {
        int attempts = (event.getAttempts() == null ? 0 : event.getAttempts()) + 1;
        if (attempts >= MAX_ATTEMPTS) {
            outboxMapper.update(null, new LambdaUpdateWrapper<WishOutboxEvent>()
                    .set(WishOutboxEvent::getStatus, "DEAD")
                    .set(WishOutboxEvent::getAttempts, attempts)
                    .set(WishOutboxEvent::getLeaseOwner, instanceId)
                    .set(WishOutboxEvent::getLeaseUntil, null)
                    .eq(WishOutboxEvent::getEventId, event.getEventId())
                    .eq(WishOutboxEvent::getStatus, "PENDING"));
            log.error("[OUTBOX DEAD] eventId={} type={} aggregate={}/{}——需人工按原 eventId 重试",
                    event.getEventId(), event.getEventType(), event.getAggregateType(), event.getAggregateId(), cause);
            return;
        }
        long backoff = BACKOFF_SECONDS[Math.min(attempts - 1, BACKOFF_SECONDS.length - 1)];
        outboxMapper.update(null, new LambdaUpdateWrapper<WishOutboxEvent>()
                .set(WishOutboxEvent::getAttempts, attempts)
                .set(WishOutboxEvent::getNextAttemptAt,
                        LocalDateTime.now(ZoneId.of("UTC")).plusSeconds(backoff))
                .set(WishOutboxEvent::getLeaseUntil, null)
                .eq(WishOutboxEvent::getEventId, event.getEventId())
                .eq(WishOutboxEvent::getStatus, "PENDING"));
        log.warn("[OUTBOX RETRY] eventId={} type={} attempts={} next={}s cause={}",
                event.getEventId(), event.getEventType(), attempts, backoff, exMessage(cause));
    }

    private static String exMessage(Exception cause) {
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    /** 已知事件类型的既有 tag（消费端 selectorExpression 依赖）；未知类型回退小驼峰。 */
    static String tagOf(String eventType) {
        return switch (eventType) {
            case "HelpedRecorded" -> RocketMQConfig.WISH_TAG_STAT_SYNC;
            case "WishFulfilled" -> RocketMQConfig.WISH_TAG_FULFILLED;
            case "WishModerated" -> RocketMQConfig.WISH_TAG_AUDITED;
            default -> camelToTag(eventType);
        };
    }

    /** WishFulfilled → wish-fulfilled 事件标签；未知类型回退小驼峰原名。 */
    static String camelToTag(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return "outbox";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : eventType.toCharArray()) {
            if (Character.isUpperCase(c)) {
                sb.append('-').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("outbox payload 序列化失败", ex);
        }
    }
}
