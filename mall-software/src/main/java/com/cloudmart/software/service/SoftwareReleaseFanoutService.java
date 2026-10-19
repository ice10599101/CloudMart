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
    private final com.cloudmart.software.repository.SoftwareReleaseMapper releaseMapper;
    private final com.cloudmart.software.repository.SoftwareSourceMapper sourceMapper;
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
                             LocalDateTime publishedAt, Long previousRevisionId) {
        SoftwareFanoutTask task = new SoftwareFanoutTask();
        task.setSoftwareId(softwareId);
        task.setRevisionId(revisionId);
        task.setPublicationSeq(publicationSeq);
        task.setPublishedAt(publishedAt);
        task.setCursorSubscriptionId(0L);
        // SF05：冻结本次发布的业务变化判定与平台/渠道集合——纯文案/排序修改不通知；
        // 管理员可在后台按 task 查看触发原因（business_change + platforms/channels）
        ReleaseScope scope = computeReleaseScope(revisionId);
        task.setBusinessChange(hasBusinessChange(previousRevisionId, scope));
        task.setPlatforms(String.join(",", scope.platforms()));
        task.setChannels(String.join(",", scope.channels()));
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
            // SW10：以任务冻结的 revisionId 投递——旧发布任务不得借用最新发布指针串线
            SoftwareFanoutTask task = fanoutTaskMapper.findBySoftwareAndSeq(
                    head.getSoftwareId(), head.getPublicationSeq());
            if (task == null || !Objects.equals(task.getPublicationSeq(),
                    software.getPublicationSeq())) {
                // 任务丢失或已被新发布取代：未发投递显式 SUPERSEDED，由最新事件接续；
                // 已发送事实保留，绝不在旧 eventId 下换内容投递
                deliveryMapper.markSuperseded(head.getSoftwareId(), head.getPublicationSeq(), now);
                continue;
            }
            // SW10：重试时核对提醒偏好——退订/关闭提醒的未发行取消（不新增事后订阅者）
            List<Long> userIds = rows.stream().map(SoftwareSubscriptionDelivery::getUserId)
                    .distinct().toList();
            Set<Long> mutedUsers = preferenceMapper.selectList(
                            new LambdaQueryWrapper<SoftwareUserPreference>()
                                    .in(SoftwareUserPreference::getUserId, userIds)
                                    .eq(SoftwareUserPreference::getUpdateNotifications, false))
                    .stream().map(SoftwareUserPreference::getUserId).collect(Collectors.toSet());
            if (!mutedUsers.isEmpty()) {
                deliveryMapper.cancelMuted(head.getSoftwareId(), head.getPublicationSeq(),
                        mutedUsers, now);
            }
            List<Long> deliverIds = userIds.stream()
                    .filter(userId -> !mutedUsers.contains(userId)).toList();
            if (deliverIds.isEmpty()) {
                continue;
            }
            boolean sent = deliver(head.getSoftwareId(), head.getPublicationSeq(),
                    task.getRevisionId(), deliverIds);
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
        // SW10：本轮批次数用尽是"正常让出"而非失败——不增加失败次数，
        // 否则大订阅集合会在无异常时被误判 FAILED
        yieldTask(task, leaseVersion, Duration.ofSeconds(30));
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

        // SF05：纯文案/排序修改（无业务版本变化）——不生成投递，任务完成并留触发原因
        if (!Boolean.TRUE.equals(task.getBusinessChange())) {
            deliveryMapper.cancelPending(task.getSoftwareId(), task.getPublicationSeq(), now);
            return new Batch(false, true, List.of(), List.of());
        }
        java.util.Set<String> releasePlatforms = splitCsv(task.getPlatforms());
        java.util.Set<String> releaseChannels = splitCsv(task.getChannels());
        // 非作者 + 未关闭更新提醒（偏好缺省视为开启）+ 平台/渠道偏好与本次发布有交集（NULL=ALL）
        List<Long> candidateIds = batch.stream()
                .filter(sub -> matchesPreference(sub.getPlatforms(), releasePlatforms)
                        && matchesPreference(sub.getChannels(), releaseChannels))
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
            // SW10：响应 success 与数据语义都要核对——"新创建数=0"代表已去重成功，不算失败
            sent = response != null && response.success() && response.data() != null;
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

    /** SW10：正常让出——游标已推进、无异常，只推迟下次执行，不增加失败次数 */
    private void yieldTask(SoftwareFanoutTask task, int leaseVersion, Duration defer) {
        fanoutTaskMapper.finish(task.getId(), "PENDING",
                task.getAttempts() == null ? 0 : task.getAttempts(),
                LocalDateTime.now(clock).plus(defer), task.getLastError(),
                leaseVersion, workerId, LocalDateTime.now(clock));
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

    // ------------------------------------------------------------------
    // SF05：业务版本变化判定（不依赖版本字符串大小；不恢复 publicationSeq 之外的新序号）
    // ------------------------------------------------------------------

    /** 本次发布的业务签名：releaseKey → (sourceKey → 指纹)，及平台/渠道集合 */
    private record ReleaseScope(
            java.util.Map<String, java.util.Map<String, String>> releaseSources,
            java.util.Set<String> platforms,
            java.util.Set<String> channels) {
    }

    private ReleaseScope computeReleaseScope(Long revisionId) {
        var releases = releaseMapper.selectList(
                new LambdaQueryWrapper<com.cloudmart.software.entity.SoftwareRelease>()
                        .eq(com.cloudmart.software.entity.SoftwareRelease::getRevisionId, revisionId));
        java.util.Map<String, java.util.Map<String, String>> releaseSources = new java.util.LinkedHashMap<>();
        java.util.Set<String> platforms = new java.util.TreeSet<>();
        java.util.Set<String> channels = new java.util.TreeSet<>();
        for (var release : releases) {
            if (release.getChannel() != null) {
                channels.add(release.getChannel().name());
            }
            releaseSources.put(release.getReleaseKey(), new java.util.LinkedHashMap<>());
        }
        if (!releases.isEmpty()) {
            var sources = sourceMapper.selectList(
                    new LambdaQueryWrapper<com.cloudmart.software.entity.SoftwareSource>()
                            .in(com.cloudmart.software.entity.SoftwareSource::getReleaseId,
                                    releases.stream().map(
                                            com.cloudmart.software.entity.SoftwareRelease::getId).toList()));
            java.util.Map<Long, String> releaseKeyById = releases.stream().collect(
                    java.util.stream.Collectors.toMap(
                            com.cloudmart.software.entity.SoftwareRelease::getId,
                            com.cloudmart.software.entity.SoftwareRelease::getReleaseKey));
            for (var source : sources) {
                String releaseKey = releaseKeyById.get(source.getReleaseId());
                if (releaseKey == null) {
                    continue;
                }
                releaseSources.get(releaseKey).put(source.getSourceKey(), source.getContentFingerprint());
                if (source.getPlatform() != null) {
                    platforms.add(source.getPlatform().name());
                }
            }
        }
        return new ReleaseScope(releaseSources, platforms, channels);
    }

    /**
     * 与上一线上快照比较：新增 releaseKey、新增 sourceKey、指纹变化（含换链）均视为
     * 业务版本变化；description/排序等纯文案修改不触发通知。
     */
    private boolean hasBusinessChange(Long previousRevisionId, ReleaseScope current) {
        if (previousRevisionId == null) {
            return true;
        }
        ReleaseScope previous = computeReleaseScope(previousRevisionId);
        for (var entry : current.releaseSources().entrySet()) {
            var prevSources = previous.releaseSources().get(entry.getKey());
            if (prevSources == null) {
                return true; // 新版本
            }
            for (var sourceEntry : entry.getValue().entrySet()) {
                String prevFingerprint = prevSources.get(sourceEntry.getKey());
                if (prevFingerprint == null || !prevFingerprint.equals(sourceEntry.getValue())) {
                    return true; // 新来源或内容变化（含换链）
                }
            }
        }
        return false;
    }

    private java.util.Set<String> splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return java.util.Set.of();
        }
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim).filter(x -> !x.isEmpty())
                .collect(java.util.stream.Collectors.toSet());
    }

    /** SF05：偏好为空=全部；有偏好时须与本次发布集合有交集 */
    private boolean matchesPreference(String preference, java.util.Set<String> releaseValues) {
        java.util.Set<String> wanted = splitCsv(preference);
        if (wanted.isEmpty() || releaseValues.isEmpty()) {
            return true;
        }
        for (String item : wanted) {
            if (releaseValues.contains(item)) {
                return true;
            }
        }
        return false;
    }
}