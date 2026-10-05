package com.cloudmart.common.async.outbox;

import com.cloudmart.common.async.mapper.OutboxEventMapper;
import org.springframework.beans.factory.ObjectProvider;
import lombok.RequiredArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Outbox 运营查询/重试服务（T16 异常处理中心）：供各域 Admin 端点复用——
 * mall-admin 汇总脱敏视图，重试在各业务域执行（管理服务不直接写他库）。
 *
 * <p>脱敏约定：视图不含 payload（可能携带业务正文/敏感字段），仅暴露
 * eventId/eventType/aggregate/attempts/lastError（已由投递器 sanitize）；
 * 重试仅接受 DEAD_LETTER（FAILED/PENDING 由投递器自动退避处理中）。</p>
 */
@RequiredArgsConstructor
public class OutboxOperationsService {

    /** 惰性解析：装配条件为 OutboxDelivery 存在（非 mapper 类型匹配），mapper 缺失时方法级明确失败 */
    private final ObjectProvider<OutboxEventMapper> outboxEventMapper;

    private OutboxEventMapper mapper() {
        OutboxEventMapper mapper = outboxEventMapper.getIfAvailable();
        if (mapper == null) {
            throw new IllegalStateException("OutboxEventMapper 未装配（本模块未接入 outbox 存储）");
        }
        return mapper;
    }

    /** 脱敏视图行（不含 payload） */
    public record OutboxTaskView(String eventId, String eventType, String aggregateId,
                                 String status, Integer attempts, String lastError,
                                 String createdAt, String updatedAt) {
    }

    /** 分页查询：status 空=全部非 SENT；LIMIT/OFFSET 手写分页（mall-common 无 MP 依赖） */
    public List<OutboxTaskView> page(String status, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = Math.max(page - 1, 0) * safeSize;
        String st = status == null || status.isBlank() ? null : status.toUpperCase();
        List<OutboxEventEntity> rows = mapper().selectForOperations(st, safeSize, offset);
        return rows.stream().map(this::toView).toList();
    }

    /** 各状态计数（告警区分"按计划重试"与"死信"） */
    public Map<String, Long> stats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        for (String status : List.of("PENDING", "SENDING", "FAILED", "DEAD_LETTER")) {
            stats.put(status, mapper().countByStatus(status));
        }
        return stats;
    }

    /**
     * 重试死信：DEAD_LETTER → PENDING（attempts 归零）；非死信或已被他人重试返回 false。
     * 重试受理不等于投递成功——投递仍由 OutboxPublisher 按退避执行。
     */
    public boolean retryDeadLetter(String eventId) {
        OutboxEventEntity event = mapper().findByEventId(eventId);
        if (event == null || !"DEAD_LETTER".equals(event.getStatus())) {
            return false;
        }
        return mapper().retryDeadLetter(event.getId()) == 1;
    }

    private OutboxTaskView toView(OutboxEventEntity e) {
        return new OutboxTaskView(
                e.getEventId(), e.getEventType(), e.getAggregateId(),
                e.getStatus(), e.getAttempts(), e.getLastError(),
                e.getCreatedAt() == null ? null : e.getCreatedAt().toString(),
                e.getUpdatedAt() == null ? null : e.getUpdatedAt().toString());
    }
}
