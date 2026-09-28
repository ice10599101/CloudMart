package com.cloudmart.common.async.compensation;

import com.cloudmart.common.async.mapper.CompensationTaskMapper;
import com.cloudmart.common.async.outbox.OutboxRetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 补偿任务服务（ASYNC-01）：
 * <ul>
 *   <li>{@link #createIfAbsent}：业务动作失败时登记持久化待办（幂等）；</li>
 *   <li>{@link #runDueTasks}：后台批量执行到期任务，按 action 分发给注册的
 *       {@link CompensationHandler}；租约抢占保证多实例安全与宕机接管；</li>
 *   <li>重试超限转 DEAD_LETTER（工作台人工处理 + 告警）。</li>
 * </ul>
 */
public class CompensationTaskService {

    private static final Logger log = LoggerFactory.getLogger(CompensationTaskService.class);

    private final CompensationTaskMapper mapper;
    private final Map<String, CompensationHandler> handlers = new ConcurrentHashMap<>();
    private final OutboxRetryPolicy retryPolicy;
    private final int batchSize;
    private final int leaseSeconds;
    private final String workerId;

    public CompensationTaskService(CompensationTaskMapper mapper, List<CompensationHandler> handlers,
                                   OutboxRetryPolicy retryPolicy, int batchSize, int leaseSeconds) {
        this.mapper = mapper;
        handlers.forEach(h -> this.handlers.put(h.action(), h));
        this.retryPolicy = retryPolicy;
        this.batchSize = batchSize;
        this.leaseSeconds = leaseSeconds;
        this.workerId = java.util.UUID.randomUUID().toString();
    }

    /**
     * 登记补偿任务（幂等），独立事务提交（ASYNC-01 断点 5）。
     *
     * <p>语义：登记的是"外部效果已发生或可能已发生"（RPC 已尝试）之后的恢复待办——
     * REQUIRED 下外层事务回滚会连补偿记录一并吞掉，库存/余额将无法恢复。
     * 独立事务保证登记在本地回滚后幸存；任务本身幂等（insertIfAbsent），调用方
     * 在尚未产生外部效果前的纯业务补偿不应使用本方法。</p>
     *
     * @param taskId 业务幂等键，如 stock-confirm:{orderId}:{skuId}
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createIfAbsent(String taskId, String action, String aggregateId, String payload) {
        CompensationTaskEntity task = new CompensationTaskEntity();
        task.setTaskId(taskId);
        task.setAction(action);
        task.setAggregateId(aggregateId);
        task.setPayload(payload);
        int inserted = mapper.insertIfAbsent(task);
        if (inserted == 0) {
            log.info("[ASYNC01] 补偿任务已存在（幂等忽略） taskId={}", taskId);
        }
    }

    /**
     * ASYNC-01：补偿执行轮询——可配置 fixedDelay（默认 5 秒）。
     * 此前方法无 @Scheduled 且无生产调用入口，补偿任务永远不被执行。
     */
    @Scheduled(fixedDelayString = "${cloudmart.async.compensation.run-delay-ms:5000}")
    public void runDueTasks() {
        try {
            int claimed = mapper.claimBatch(workerId, leaseSeconds, batchSize);
            if (claimed == 0) {
                return;
            }
            List<CompensationTaskEntity> batch = mapper.selectClaimed(workerId, leaseSeconds, batchSize);
            for (CompensationTaskEntity task : batch) {
                runOne(task);
            }
        } catch (Exception e) {
            log.error("[ASYNC01] 补偿任务轮次异常: {}", e.getMessage());
        }
    }

    private void runOne(CompensationTaskEntity task) {
        CompensationHandler handler = handlers.get(task.getAction());
        if (handler == null) {
            log.error("[ASYNC01] 补偿任务无对应处理器 action={} taskId={}（保持 PENDING 等待处理器上线）",
                    task.getAction(), task.getTaskId());
            // 释放锁并回 PENDING：处理器上线后自然执行
            mapper.markFailure(task.getId(), Integer.MAX_VALUE, 60_000L, "no handler registered");
            return;
        }
        try {
            handler.execute(task.getAggregateId(), task.getPayload());
            mapper.markSucceeded(task.getId());
            log.info("[ASYNC01] 补偿任务成功 action={} taskId={}", task.getAction(), task.getTaskId());
        } catch (Exception e) {
            long backoff = retryPolicy.nextBackoffMillis(task.getAttempts() == null ? 0 : task.getAttempts());
            int updated = mapper.markFailure(task.getId(), retryPolicy.maxAttempts(), backoff,
                    sanitize(e.getMessage()));
            if (updated > 0) {
                log.warn("[ASYNC01] 补偿任务失败 action={} taskId={} attempts={} backoff={}ms error={}",
                        task.getAction(), task.getTaskId(),
                        (task.getAttempts() == null ? 0 : task.getAttempts()) + 1, backoff, e.getMessage());
            }
        }
    }

    /** 错误信息脱敏截断（与 Outbox 同规则，工作台可见，禁止携带敏感数据） */
    private static String sanitize(String message) {
        if (message == null) {
            return "unknown error";
        }
        String cleaned = message.replaceAll("(?i)(password|token|secret|authorization)=[^,&\s]+", "$1=***");
        return cleaned.length() > 1000 ? cleaned.substring(0, 1000) : cleaned;
    }
}
