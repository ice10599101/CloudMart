package com.cloudmart.software.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.software.config.SoftwareMetrics;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareFanoutTask;
import com.cloudmart.software.entity.SoftwareSubscription;
import com.cloudmart.software.entity.SoftwareSubscriptionDelivery;
import com.cloudmart.software.entity.SoftwareUserPreference;
import com.cloudmart.software.enums.SoftwareLifecycle;
import com.cloudmart.software.feign.SoftwareNotificationFeignClient;
import com.cloudmart.software.repository.SoftwareFanoutTaskMapper;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareSubscriptionDeliveryMapper;
import com.cloudmart.software.repository.SoftwareSubscriptionMapper;
import com.cloudmart.software.repository.SoftwareUserPreferenceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 订阅更新广播（方案 9.4，P1）：更新发布 → fanout 任务 → 游标分批扫描订阅 →
 * 投递行 → 批量通知（通知服务按 eventId+userId 去重）。
 *
 * <p>不变量：不能一次读取全部订阅（游标批次）；批次插入投递行与推进游标同事务；
 * 领取带租约版本 fencing，宕机可续跑；投递前重新核对软件状态与提醒偏好，
 * 下架即取消本次未发投递；失败按指数退避（最多 12 次）转 FAILED。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoftwareReleaseFanoutService {

    /** 单批扫描订阅数（有界，避免一次读取全部粉丝） */
    static final int BATCH_SIZE = 200;
    /** 单条投递最大尝试次数（超限转 FAILED，人工重试保留同一通知去重键） */
    static final int DELIVERY_MAX_ATTEMPTS = 12;
    /** fanout 任务最大续跑次数（超限转 FAILED 并在后台可见原因） */
    static final int TASK_MAX_ATTEMPTS = 5;
    /** 任务租约时长（多实例 fencing） */
    static final Duration LEASE_DURATION = Duration.ofMinutes(2);
    /** 通知服务不可用时的任务退避间隔 */
    static final Duration DEFER_DELAY = Duration.ofSeconds(60);
    /** 单轮调度内单个任务最多推进的批次数（让出线程，下轮继续） */
    private static final int MAX_ROUNDS_PER_RUN = 20;

    private final SoftwareFanoutTaskMapper fanoutTaskMapper;
    private final SoftwareSubscriptionMapper subscriptionMapper;
    private final SoftwareSubscriptionDeliveryMapper deliveryMapper;
    private final SoftwareUserPreferenceMapper preferenceMapper;
    private final SoftwareMapper softwareMapper;
    private final SoftwareNotificationFeignClient notificationClient;
    private final SoftwareMetrics softwareMetrics;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /** 本实例的租约身份（多实例互斥/接管） */
    private final String workerId = UUID.randomUUID().toString();

    /**
     * 登记 fanout 任务（更新发布时同事务调用；唯一键 (softwareId,publicationSeq) 幂等）。
     */
    @Transactional
    public void registerTask(Long softwareId, Long revisionId, Integer publicationSeq,
                             LocalDateTime publishedAt) {
        SoftwareFanoutTask task = new SoftwareFanoutTask();
        task.setSoftwareId(softwareId);
        task.setRevisionId(revisionId);
        task.setPublicationSeq(publicationSeq);
        task.setPublishedAt(publishedAt);
        task.setCursorSubscriptionId(0L);
        task.setStatus("PENDING");
        task.setLeaseVersion(0);
        task.setAttempts(0);
        LocalDateTime now = LocalDateTime.now(clock);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        try {
            fanoutTaskMapper.insert(task);
        } catch (DuplicateKeyException e) {
            // 同一次发布重复登记：唯一键兜住，不重复广播
            log.info("fanout 任务已存在，跳过登记 softwareId={} seq={}", softwareId, publicationSeq);
        }
    }

    /** 调度入口：先补投失败/到期的投递行，再推进 fanout 任务 */
    public void runOnce() {
        try {
            retryDueDeliveries();
        } catch (Exception e) {
            log.error("订阅投递重试异常: {}", e.getMessage(), e);
        }
        try {
            for (int i = 0; i < 3; i++) {
                if (!advanceOneTask()) {
                    return;
                }
            }
        } catch (Exception e) {
            log.error("fanout 任务推进异常: {}", e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------
    // 投递重试（失败/到期的 PENDING 行）
    // ------------------------------------------------------------------

    private void retryDueDeliveries() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<SoftwareSubscriptionDelivery> due = deliveryMapper.findDue(now, BATCH_SIZE);
        if (due.isEmpty()) {
            return;
        }
        var groups = due.stream().collect(Collectors.groupingBy(
                row -> row.getSoftwareId() + ":" + row.getPublicationSeq()));
        for (List<SoftwareSubscriptionDelivery> rows : groups.values()) {
            SoftwareSubscriptionDelivery head = rows.get(0);
            Software software = softwareMapper.selectById(head.getSoftwareId());
            if (software == null || software.getDeletedAt() != null
                    || software.getLifecycle() != SoftwareLifecycle.ACTIVE) {
                // 下架/删除：取消未发投递，不再打扰订阅者
                deliveryMapper.cancelPending(head.getSoftwareId(), head.getPublicationSeq(), now);
                continue;
            }
            List<Long> userIds = rows.stream().map(SoftwareSubscriptionDelivery::getUserId)
                    .distinct().toList();
            boolean sent = deliver(head.getSoftwareId(), head.getPublicationSeq(),
                    software.getPublishedRevisionId(), userIds);
            markResults(rows, sent, now);
        }
    }

    // ------------------------------------------------------------------
    // fanout 任务推进
    // ------------------------------------------------------------------

    private boolean advanceOneTask() {
        LocalDateTime now = LocalDateTime.now(clock);
        SoftwareFanoutTask due = fanoutTaskMapper.findDue(now);
        if (due == null) {
            return false;
        }
        int claimed = fanoutTaskMapper.claim(due.getId(), workerId,
                now.plus(LEASE_DURATION), due.getLeaseVersion(), now);
        if (claimed == 0) {
            return false; // 被其他实例抢走
        }
        int leaseVersion = due.getLeaseVersion() + 1;
        try {
            processTask(due, leaseVersion);
        } catch (Exception e) {
            log.warn("fanout 任务处理失败 taskId={}: {}", due.getId(), e.getMessage());
            finishTask(due, leaseVersion, "PENDING", "处理异常：" + shortMessage(e), DEFER_DELAY);
        }
        return true;
    }

    private void processTask(SoftwareFanoutTask task, int leaseVersion) {
        for (int round = 0; round < MAX_ROUNDS_PER_RUN; round++) {
            Batch batch = transactionTemplate.execute(
                    status -> scanAndCreateBatch(task, leaseVersion));
            if (batch == null) {
                return;
            }
            if (batch.cancelled()) {
                finishTask(task, leaseVersion, "DONE", "软件已下架，取消本次未发通知", null);
                return;
            }
            boolean sent = true;
            if (!batch.userIds().isEmpty()) {
                sent = deliver(task.getSoftwareId(), task.getPublicationSeq(),
                        task.getRevisionId(), batch.userIds());
                markResults(batch.rows(), sent, LocalDateTime.now(clock));
            }
            if (batch.reachedEnd()) {
                finishTask(task, leaseVersion, "DONE", null, null);
                return;
            }
            if (!sent) {
                // 通知服务不可用：投递行已保留 PENDING 走退避重试；任务稍后再推进游标
                finishTask(task, leaseVersion, "PENDING", "通知服务暂不可用，改由投递重试", DEFER_DELAY);
                return;
            }
            renewLease(task, leaseVersion);
        }
        // 本轮批次数用尽：让出，下轮继续（租约到期后可被接管）
        finishTask(task, leaseVersion, "PENDING", null, Duration.ofSeconds(30));
    }

    /**
     * 单批扫描（事务内）：插入投递行与推进游标同事务；投递前核对软件状态、
     * 订阅时间（创建时间不晚于发布时间）、非作者、提醒偏好。
     */
    private Batch scanAndCreateBatch(SoftwareFanoutTask task, int leaseVersion) {
        LocalDateTime now = LocalDateTime.now(clock);
        Software software = softwareMapper.selectById(task.getSoftwareId());
        if (software == null || software.getDeletedAt() != null
                || software.getLifecycle() != SoftwareLifecycle.ACTIVE) {
            deliveryMapper.cancelPending(task.getSoftwareId(), task.getPublicationSeq(), now);
            return new Batch(true, true, List.of(), List.of());
        }
        if (!Objects.equals(software.getPublicationSeq(), task.getPublicationSeq())) {
            // 已被后续发布覆盖：停止继续广播（已生成的投递行由重试路径收敛）
            return new Batch(false, true, List.of(), List.of());
        }
        List<SoftwareSubscription> batch = subscriptionMapper.selectList(
                new LambdaQueryWrapper<SoftwareSubscription>()
                        .eq(SoftwareSubscription::getSoftwareId, task.getSoftwareId())
                        .gt(SoftwareSubscription::getId, task.getCursorSubscriptionId())
                        .le(SoftwareSubscription::getCreatedAt, task.getPublishedAt())
                        .orderByAsc(SoftwareSubscription::getId)
                        .last("LIMIT " + BATCH_SIZE));
        if (batch.isEmpty()) {
            return new Batch(false, true, List.of(), List.of());
        }
        Long newCursor = batch.get(batch.size() - 1).getId();

        // 非作者 + 未关闭更新提醒（偏好缺省视为开启）
        List<Long> candidateIds = batch.stream()
                .map(SoftwareSubscription::getUserId)
                .filter(userId -> !userId.equals(software.getOwnerId()))
                .distinct()
                .toList();
        Set<Long> mutedUsers = candidateIds.isEmpty() ? Set.of()
                : preferenceMapper.selectList(new LambdaQueryWrapper<SoftwareUserPreference>()
                                .in(SoftwareUserPreference::getUserId, candidateIds)
                                .eq(SoftwareUserPreference::getUpdateNotifications, false))
                        .stream().map(SoftwareUserPreference::getUserId)
                        .collect(Collectors.toSet());
        List<Long> deliverIds = candidateIds.stream()
                .filter(userId -> !mutedUsers.contains(userId))
                .toList();

        List<SoftwareSubscriptionDelivery> created = new ArrayList<>();
        for (Long userId : deliverIds) {
            SoftwareSubscriptionDelivery row = new SoftwareSubscriptionDelivery();
            row.setSoftwareId(task.getSoftwareId());
            row.setPublicationSeq(task.getPublicationSeq());
            row.setUserId(userId);
            row.setStatus("PENDING");
            row.setAttempts(0);
            row.setLeaseVersion(0);
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            try {
                deliveryMapper.insert(row);
                created.add(row);
            } catch (DuplicateKeyException e) {
                // 崩溃重跑同一批次：唯一键 (softwareId,seq,userId) 收敛
                log.debug("投递行已存在 softwareId={} seq={} userId={}",
                        task.getSoftwareId(), task.getPublicationSeq(), userId);
            }
        }
        fanoutTaskMapper.advanceCursor(task.getId(), newCursor, leaseVersion, workerId,
                LocalDateTime.now(clock));
        return new Batch(false, batch.size() < BATCH_SIZE, deliverIds, created);
    }

    // ------------------------------------------------------------------
    // 投递与状态标记
    // ------------------------------------------------------------------

    private boolean deliver(Long softwareId, Integer publicationSeq, Long revisionId,
                            List<Long> userIds) {
        long start = System.nanoTime();
        boolean sent = false;
        try {
            var response = notificationClient.releasePublished(
                    new com.cloudmart.software.dto.software.SoftwareReleaseNotifyRequest(
                            softwareId, publicationSeq, revisionId, userIds));
            sent = response != null && response.data() != null;
            return sent;
        } catch (Exception e) {
            log.warn("订阅提醒投递失败 softwareId={} seq={} users={}: {}",
                    softwareId, publicationSeq, userIds.size(), e.getMessage());
            return false;
        } finally {
            softwareMetrics.recordFanoutDelivery(sent, (System.nanoTime() - start) / 1_000_000);
        }
    }

    private void markResults(List<SoftwareSubscriptionDelivery> rows, boolean sent,
                             LocalDateTime now) {
        for (SoftwareSubscriptionDelivery row : rows) {
            if (sent) {
                deliveryMapper.markSent(row.getId(), now);
                continue;
            }
            int attempts = (row.getAttempts() == null ? 0 : row.getAttempts()) + 1;
            if (attempts >= DELIVERY_MAX_ATTEMPTS) {
                deliveryMapper.markRetry(row.getId(), "FAILED", attempts, null, now);
                log.error("订阅投递超过最大重试次数转 FAILED deliveryId={} userId={}",
                        row.getId(), row.getUserId());
            } else {
                deliveryMapper.markRetry(row.getId(), "PENDING", attempts,
                        now.plus(backoff(attempts)), now);
            }
        }
    }

    /** 指数退避：2^attempts 秒，封顶 1 小时（12 次后触顶） */
    static Duration backoff(int attempts) {
        long seconds = Math.min(1L << Math.min(attempts, 12), 3600);
        return Duration.ofSeconds(seconds);
    }

    private void renewLease(SoftwareFanoutTask task, int leaseVersion) {
        fanoutTaskMapper.renewLease(task.getId(),
                LocalDateTime.now(clock).plus(LEASE_DURATION), leaseVersion, workerId,
                LocalDateTime.now(clock));
    }

    private void finishTask(SoftwareFanoutTask task, int leaseVersion, String status,
                            String note, Duration defer) {
        int attempts = (task.getAttempts() == null ? 0 : task.getAttempts())
                + ("DONE".equals(status) ? 0 : 1);
        String resolvedStatus = status;
        if (!"DONE".equals(status) && attempts >= TASK_MAX_ATTEMPTS) {
            resolvedStatus = "FAILED";
        }
        fanoutTaskMapper.finish(task.getId(), resolvedStatus, attempts,
                "DONE".equals(status) || resolvedStatus.equals("FAILED") || defer == null
                        ? null : LocalDateTime.now(clock).plus(defer),
                note == null ? task.getLastError() : note,
                leaseVersion, workerId, LocalDateTime.now(clock));
    }

    private String shortMessage(Exception e) {
        String message = e.getMessage();
        return message == null ? e.getClass().getSimpleName()
                : message.substring(0, Math.min(message.length(), 200));
    }

    /** 单批扫描结果 */
    private record Batch(boolean cancelled, boolean reachedEnd, List<Long> userIds,
                         List<SoftwareSubscriptionDelivery> rows) {
    }

    /** 供测试与监控：当前实例领取身份 */
    String workerId() {
        return workerId;
    }
}
