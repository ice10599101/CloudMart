package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.wish.config.RocketMQConfig;
import com.cloudmart.wish.entity.WishOutboxEvent;
import com.cloudmart.wish.repository.WishOutboxMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.skywalking.apm.toolkit.trace.TraceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
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
    /** 事件 envelope 版本：新增/变更 envelope 字段时递增，消费端据此兼容 */
    static final int SCHEMA_VERSION = 1;
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
     *
     * <p>T01：调用方常传 {@code Map.of} 等不可变 Map——本方法对 payload 做防御性复制后
     * 写入 envelope，绝不修改调用方对象（旧实现直接 {@code payload.put} 会抛
     * {@code UnsupportedOperationException} 并连带回滚审核/还愿/胶囊等业务事务）。
     * null payload 归一为空 body。投递体除调用方字段外包含标准 envelope：
     * {@code eventId/eventType/schemaVersion/aggregateType/aggregateId/aggregateVersion/
     * occurredAt/traceId}；{@code eventId} 恒为行主键，重试投递不变，消费端按其去重
     * （类型化旧消费端以 {@code ignoreUnknown=true} 兼容 envelope 扩展字段）。</p>
     */
    public void publish(String aggregateType, Long aggregateId, long aggregateVersion,
                        String eventType, Map<String, Object> payload) {
        WishOutboxEvent event = new WishOutboxEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setAggregateVersion(aggregateVersion);
        event.setEventType(eventType);
        Map<String, Object> body = payload == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(payload);
        // envelope 字段后写——调用方同名字段被 envelope 权威值覆盖，避免伪造/漂移
        body.put("eventId", event.getEventId());
        body.put("eventType", eventType);
        body.put("schemaVersion", SCHEMA_VERSION);
        body.put("aggregateType", aggregateType);
        body.put("aggregateId", aggregateId);
        body.put("aggregateVersion", aggregateVersion);
        body.put("occurredAt", Instant.now());
        body.put("traceId", currentTraceId());
        event.setPayload(toJson(body));
        event.setStatus("PENDING");
        event.setAttempts(0);
        event.setNextAttemptAt(LocalDateTime.now(ZoneId.of("UTC")));
        event.setCreatedAt(LocalDateTime.now(ZoneId.of("UTC")));
        outboxMapper.insert(event);
    }

    /** SkyWalking traceId（agent 未挂载时为空）；可观测字段缺失不影响事件发布主流程。 */
    private static String currentTraceId() {
        try {
            String traceId = TraceContext.traceId();
            return traceId == null || traceId.isBlank() ? null : traceId;
        } catch (Throwable ex) {
            return null;
        }
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

    /**
     * 条件 UPDATE 抢租约：只有占用成功的实例投递；认领递增 leaseVersion（T16 fencing），
     * 认领成功后回读权威租约版本（内存实体版本落后于 DB 自增，不能直接用于回写校验）。
     */
    private boolean tryLease(WishOutboxEvent event) {
        LocalDateTime leaseUntil = LocalDateTime.now(ZoneId.of("UTC")).plusSeconds(30);
        int claimed = outboxMapper.update(null, new LambdaUpdateWrapper<WishOutboxEvent>()
                .set(WishOutboxEvent::getLeaseOwner, instanceId)
                .set(WishOutboxEvent::getLeaseUntil, leaseUntil)
                .setSql("lease_version = lease_version + 1")
                .eq(WishOutboxEvent::getEventId, event.getEventId())
                .eq(WishOutboxEvent::getStatus, "PENDING")
                .and(w -> w.isNull(WishOutboxEvent::getLeaseUntil)
                        .or().lt(WishOutboxEvent::getLeaseUntil, LocalDateTime.now(ZoneId.of("UTC")))));
        if (claimed != 1) {
            return false;
        }
        WishOutboxEvent leased = outboxMapper.selectOne(new LambdaQueryWrapper<WishOutboxEvent>()
                .eq(WishOutboxEvent::getEventId, event.getEventId()));
        if (leased == null || !instanceId.equals(leased.getLeaseOwner())) {
            return false;
        }
        event.setLeaseVersion(leased.getLeaseVersion());
        return true;
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

    /** T16 fencing：回写绑定租约持有者与版本——失去租约的旧实例迟到回写 0 行被拒。 */
    private void markPublished(WishOutboxEvent event) {
        int updated = outboxMapper.update(null, new LambdaUpdateWrapper<WishOutboxEvent>()
                .set(WishOutboxEvent::getStatus, "PUBLISHED")
                .set(WishOutboxEvent::getPublishedAt, LocalDateTime.now(ZoneId.of("UTC")))
                .eq(WishOutboxEvent::getEventId, event.getEventId())
                .eq(WishOutboxEvent::getStatus, "PENDING")
                .eq(WishOutboxEvent::getLeaseOwner, instanceId)
                .eq(WishOutboxEvent::getLeaseVersion, event.getLeaseVersion() == null ? 0 : event.getLeaseVersion()));
        if (updated == 0) {
            log.warn("[T16] 迟到 PUBLISHED 回写被拒（租约已被接管） eventId={}", event.getEventId());
        }
    }

    private void markRetryOrDead(WishOutboxEvent event, Exception cause) {
        int attempts = (event.getAttempts() == null ? 0 : event.getAttempts()) + 1;
        int expectedLeaseVersion = event.getLeaseVersion() == null ? 0 : event.getLeaseVersion();
        if (attempts >= MAX_ATTEMPTS) {
            int updated = outboxMapper.update(null, new LambdaUpdateWrapper<WishOutboxEvent>()
                    .set(WishOutboxEvent::getStatus, "DEAD")
                    .set(WishOutboxEvent::getAttempts, attempts)
                    .set(WishOutboxEvent::getLeaseOwner, instanceId)
                    .set(WishOutboxEvent::getLeaseUntil, null)
                    .eq(WishOutboxEvent::getEventId, event.getEventId())
                    .eq(WishOutboxEvent::getStatus, "PENDING")
                    .eq(WishOutboxEvent::getLeaseOwner, instanceId)
                    .eq(WishOutboxEvent::getLeaseVersion, expectedLeaseVersion));
            if (updated == 0) {
                log.warn("[T16] 迟到 DEAD 回写被拒（租约已被接管） eventId={}", event.getEventId());
                return;
            }
            log.error("[OUTBOX DEAD] eventId={} type={} aggregate={}/{}——需人工按原 eventId 重试",
                    event.getEventId(), event.getEventType(), event.getAggregateType(), event.getAggregateId(), cause);
            return;
        }
        long backoff = BACKOFF_SECONDS[Math.min(attempts - 1, BACKOFF_SECONDS.length - 1)];
        int updated = outboxMapper.update(null, new LambdaUpdateWrapper<WishOutboxEvent>()
                .set(WishOutboxEvent::getAttempts, attempts)
                .set(WishOutboxEvent::getNextAttemptAt,
                        LocalDateTime.now(ZoneId.of("UTC")).plusSeconds(backoff))
                .set(WishOutboxEvent::getLeaseUntil, null)
                .eq(WishOutboxEvent::getEventId, event.getEventId())
                .eq(WishOutboxEvent::getStatus, "PENDING")
                .eq(WishOutboxEvent::getLeaseOwner, instanceId)
                .eq(WishOutboxEvent::getLeaseVersion, expectedLeaseVersion));
        if (updated == 0) {
            log.warn("[T16] 迟到 RETRY 回写被拒（租约已被接管） eventId={}", event.getEventId());
            return;
        }
        log.warn("[OUTBOX RETRY] eventId={} type={} attempts={} next={}s cause={}",
                event.getEventId(), event.getEventType(), attempts, backoff, exMessage(cause));
    }

    // ==================== T16 异常处理中心：运营查询与死信重试 ====================

    /** 脱敏视图行（不含 payload——可能携带私密正文） */
    public record OutboxTaskView(String eventId, String eventType, String aggregateId,
                                 String status, Integer attempts, String lastError,
                                 String createdAt, String updatedAt) {
    }

    /** 分页查询：status 空=全部非 PUBLISHED */
    public List<OutboxTaskView> pageForOperations(String status, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = Math.max(page - 1, 0) * safeSize;
        List<WishOutboxEvent> rows = outboxMapper.selectList(
                new LambdaQueryWrapper<WishOutboxEvent>()
                        .ne(WishOutboxEvent::getStatus, "PUBLISHED")
                        .eq(status != null && !status.isBlank(), WishOutboxEvent::getStatus, status)
                        .orderByDesc(WishOutboxEvent::getCreatedAt)
                        .last("LIMIT " + safeSize + " OFFSET " + offset));
        return rows.stream().map(e -> new OutboxTaskView(
                e.getEventId(), e.getEventType(),
                e.getAggregateType() + ":" + e.getAggregateId(),
                e.getStatus(), e.getAttempts(), null,
                e.getCreatedAt() == null ? null : e.getCreatedAt().toString(),
                null)).toList();
    }

    /** 状态计数 */
    public Map<String, Long> statsForOperations() {
        Map<String, Long> stats = new java.util.LinkedHashMap<>();
        for (String st : List.of("PENDING", "PUBLISHED", "DEAD")) {
            stats.put(st, outboxMapper.selectCount(
                    new LambdaQueryWrapper<WishOutboxEvent>()
                            .eq(WishOutboxEvent::getStatus, st)));
        }
        return stats;
    }

    /**
     * T16 死信重试：DEAD → PENDING（attempts 归零、清租约）——中继自动按退避重新投递；
     * 原 eventId/payload 不变（消费端按 eventId 去重，重复投递安全）。
     */
    public boolean retryDead(String eventId) {
        WishOutboxEvent event = outboxMapper.selectById(eventId);
        if (event == null || !"DEAD".equals(event.getStatus())) {
            return false;
        }
        int updated = outboxMapper.update(null, new LambdaUpdateWrapper<WishOutboxEvent>()
                .eq(WishOutboxEvent::getEventId, eventId)
                .eq(WishOutboxEvent::getStatus, "DEAD")
                .set(WishOutboxEvent::getStatus, "PENDING")
                .set(WishOutboxEvent::getAttempts, 0)
                .set(WishOutboxEvent::getNextAttemptAt, LocalDateTime.now(ZoneId.of("UTC")))
                .set(WishOutboxEvent::getLeaseOwner, null)
                .set(WishOutboxEvent::getLeaseUntil, null));
        if (updated == 1) {
            log.info("[T16] 死信重试受理 eventId={} type={}（原 eventId/payload 不变）",
                    eventId, event.getEventType());
        }
        return updated == 1;
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
            case "CAPSULE_AVAILABLE" -> RocketMQConfig.WISH_TAG_CAPSULE_AVAILABLE;
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
