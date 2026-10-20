package com.cloudmart.software.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.software.dto.software.SoftwareReviewOutcomeVO;
import com.cloudmart.software.dto.software.SoftwareReviewRequest;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwarePublisherPermission;
import com.cloudmart.software.entity.SoftwareRelease;
import com.cloudmart.software.entity.SoftwareReviewCheckTask;
import com.cloudmart.software.entity.SoftwareRevision;
import com.cloudmart.software.entity.SoftwareRevisionMedia;
import com.cloudmart.software.entity.SoftwareSource;
import com.cloudmart.software.enums.SoftwareReviewStatus;
import com.cloudmart.software.feign.CommunitySupportFeignClient;
import com.cloudmart.software.feign.UserSupportFeignClient;
import com.cloudmart.software.repository.SoftwareLockMapper;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareReleaseMapper;
import com.cloudmart.software.repository.SoftwareReviewCheckTaskMapper;
import com.cloudmart.software.repository.SoftwareRevisionMapper;
import com.cloudmart.software.repository.SoftwareRevisionMediaMapper;
import com.cloudmart.software.repository.SoftwareSourceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SW04：提交冻结后的自动审核检查执行器。
 *
 * <p>检查任务由提交事务持久化（revisionId 唯一），本服务以租约+fencing 领取并
 * 在事务外读取冻结记录执行远程检查：通过→PENDING（等待人工）、违规→REJECTED
 * 并生成新 DRAFT、临时故障保持 CHECKING 并按指数退避重试；超过上限进入
 * DEAD_LETTER 等待后台人工重试。超时/失败绝不伪装成通过，结果只能作用于
 * 提交时冻结的 revisionId。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoftwareReviewCheckService {

    static final int MAX_ATTEMPTS = 6;
    static final Duration LEASE_DURATION = Duration.ofMinutes(2);
    static final Duration BASE_BACKOFF = Duration.ofSeconds(30);
    static final Duration MAX_BACKOFF = Duration.ofMinutes(30);

    private final SoftwareReviewCheckTaskMapper taskMapper;
    private final SoftwareRevisionMapper revisionMapper;
    private final SoftwareReleaseMapper releaseMapper;
    private final SoftwareSourceMapper sourceMapper;
    private final SoftwareRevisionMediaMapper revisionMediaMapper;
    private final SoftwareMapper softwareMapper;
    private final SoftwareLockMapper lockMapper;
    private final SoftwareApprovalService approvalService;
    private final SoftwareAuditService auditService;
    private final CommunitySupportFeignClient communitySupportFeignClient;
    private final UserSupportFeignClient userSupportFeignClient;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    /** 执行者标识：同一实例内稳定，租约回写与 fencing 以此识别。 */
    private final String workerId = "software-check-" + UUID.randomUUID();

    /**
     * 领取并处理一个到期任务。
     *
     * @return true 表示本轮有到期任务（无论是否抢到租约），调度器可继续尝试下一条
     */
    public boolean runOnce() {
        LocalDateTime now = LocalDateTime.now(clock);
        SoftwareReviewCheckTask task = taskMapper.findDue(now);
        if (task == null) {
            return false;
        }
        int claimed = taskMapper.claim(task.getId(), workerId, now.plus(LEASE_DURATION),
                task.getLeaseVersion(), now);
        if (claimed != 1) {
            log.debug("检查任务租约竞争失败 taskId={}，稍后重试", task.getId());
            return true;
        }
        // claim 成功后 lease_version 已 +1，回写必须使用新版本
        int leaseVersion = task.getLeaseVersion() + 1;
        try {
            process(task, leaseVersion, now);
        } catch (RuntimeException e) {
            log.warn("检查任务执行异常 taskId={}: {}", task.getId(), e.getMessage());
            retry(task, leaseVersion, now, errorOf(e));
        }
        return true;
    }

    private void process(SoftwareReviewCheckTask task, int leaseVersion, LocalDateTime now) {
        SoftwareRevision revision = revisionMapper.selectById(task.getRevisionId());
        if (revision == null || revision.getReviewStatus() != SoftwareReviewStatus.CHECKING) {
            finish(task, leaseVersion, "CANCELLED", task.getAttempts(), null,
                    "提交记录已不在检查中", now);
            return;
        }
        List<String> texts;
        try {
            texts = reviewTexts(revision);
        } catch (RuntimeException e) {
            retry(task, leaseVersion, now, errorOf(e));
            return;
        }
        String digest = SoftwareReviewTextDigest.digest(texts);
        if (!digest.equals(task.getContentDigest())
                || !digest.equals(revision.getContentDigest())) {
            // 完整性异常不自动拒绝、也不自动通过：保留 CHECKING 并进入死信等待人工处置
            finish(task, leaseVersion, "DEAD_LETTER", task.getAttempts(), null,
                    "冻结内容摘要不一致（expected=" + task.getContentDigest()
                            + ", actual=" + digest + "）", now);
            return;
        }

        Long ownerId = ownerOf(task.getSoftwareId());
        if (ownerId == null) {
            finish(task, leaseVersion, "CANCELLED", task.getAttempts(), null,
                    "软件不存在或已删除", now);
            return;
        }

        Boolean accountAvailable;
        try {
            accountAvailable = accountAvailable(ownerId);
        } catch (RuntimeException e) {
            retry(task, leaseVersion, now, "账号状态服务不可用: " + errorOf(e));
            return;
        }
        if (accountAvailable == null) {
            retry(task, leaseVersion, now, "账号状态服务不可用（空响应）");
            return;
        }
        if (!accountAvailable) {
            reject(task, leaseVersion, now, "账号状态异常，无法完成提交检查");
            return;
        }

        SoftwareReviewOutcomeVO outcome;
        try {
            long mediaCount = revisionMediaMapper.selectCount(
                    new LambdaQueryWrapper<SoftwareRevisionMedia>()
                            .eq(SoftwareRevisionMedia::getRevisionId, task.getRevisionId()));
            outcome = communitySupportFeignClient.review(new SoftwareReviewRequest(
                    task.getOperationKey(), task.getContentDigest(), texts,
                    Math.toIntExact(mediaCount))).data();
        } catch (RuntimeException e) {
            retry(task, leaseVersion, now, "内容审核服务不可用: " + errorOf(e));
            return;
        }
        if (outcome == null) {
            retry(task, leaseVersion, now, "内容审核服务不可用（空响应）");
            return;
        }
        if (!outcome.approved()) {
            String reason = outcome.reason() == null || outcome.reason().isBlank()
                    ? "内容审核未通过" : outcome.reason();
            reject(task, leaseVersion, now, "自动检查未通过：" + reason);
            return;
        }

        Transition transition = Boolean.TRUE.equals(task.getAutoPublish())
                && !outcome.needsManualReview()
                ? autoPublish(task, ownerId)
                : moveToPending(task);
        switch (transition) {
            case APPLIED -> finish(task, leaseVersion, "DONE", task.getAttempts(), null,
                    null, now);
            case CANCELLED -> finish(task, leaseVersion, "CANCELLED", task.getAttempts(), null,
                    "提交记录已不在检查中", now);
            case DIGEST_MISMATCH -> finish(task, leaseVersion, "DEAD_LETTER",
                    task.getAttempts(), null, "冻结内容摘要不一致", now);
        }
    }

    /** 自动检查通过：CHECKING→PENDING（等待人工审核）。 */
    private Transition moveToPending(SoftwareReviewCheckTask task) {
        return transactionTemplate.execute(status -> {
            SoftwareRevision revision = lockCheckable(task);
            if (revision == null) {
                return Transition.CANCELLED;
            }
            if (!digestMatches(task, revision)) {
                return Transition.DIGEST_MISMATCH;
            }
            revision.setReviewStatus(SoftwareReviewStatus.PENDING);
            revision.setReviewReason(null);
            revision.setReviewedAt(null);
            revision.setReviewerId(null);
            revisionMapper.updateById(revision);
            auditService.record(task.getSoftwareId(), task.getRevisionId(), 0L, "SYSTEM",
                    "AUTO_CHECK_PASS", "CHECKING", "PENDING",
                    "自动检查通过，等待人工审核", null);
            return Transition.APPLIED;
        });
    }

    /**
     * 直接发布权限（提交时捕获）：检查通过且无需人工复核时自动上线。
     * 锁内复核权限仍为 DIRECT；资格被关闭/模式变更时退回 PENDING 人工审核。
     */
    private Transition autoPublish(SoftwareReviewCheckTask task, Long ownerId) {
        return transactionTemplate.execute(status -> {
            SoftwarePublisherPermission permission = lockMapper.lockPublisherByUser(ownerId);
            Software locked = lockMapper.lockSoftware(task.getSoftwareId());
            if (locked == null || locked.getDeletedAt() != null) {
                return Transition.CANCELLED;
            }
            SoftwareRevision revision = lockMapper.lockRevision(task.getRevisionId());
            if (revision == null || revision.getReviewStatus() != SoftwareReviewStatus.CHECKING) {
                return Transition.CANCELLED;
            }
            if (!digestMatches(task, revision)) {
                return Transition.DIGEST_MISMATCH;
            }
            boolean direct = permission != null
                    && Boolean.TRUE.equals(permission.getCanPublish())
                    && "DIRECT".equals(permission.getPublishMode());
            if (!direct) {
                revision.setReviewStatus(SoftwareReviewStatus.PENDING);
                revision.setReviewReason(null);
                revision.setReviewedAt(null);
                revision.setReviewerId(null);
                revisionMapper.updateById(revision);
                auditService.record(task.getSoftwareId(), task.getRevisionId(), 0L, "SYSTEM",
                        "AUTO_CHECK_PASS", "CHECKING", "PENDING",
                        "直接发布资格已变更，转人工审核", null);
                return Transition.APPLIED;
            }
            revision.setReviewedAt(LocalDateTime.now(clock));
            revision.setReviewerId(null);
            revision.setReviewReason("自动检查通过（直接发布权限）");
            approvalService.publishApproved(locked, revision, 0L, "SYSTEM", "AUTO_PUBLISH",
                    "CHECKING", "自动检查通过（直接发布权限）", null);
            return Transition.APPLIED;
        });
    }

    /** 自动检查拒绝：CHECKING→REJECTED 并克隆新 DRAFT（旧记录保留审核原因）。 */
    private void reject(SoftwareReviewCheckTask task, int leaseVersion, LocalDateTime now,
                        String reason) {
        Transition transition = transactionTemplate.execute(status -> {
            SoftwareRevision revision = lockCheckable(task);
            if (revision == null) {
                return Transition.CANCELLED;
            }
            if (!digestMatches(task, revision)) {
                return Transition.DIGEST_MISMATCH;
            }
            // 软件行已在 lockCheckable 内锁定，当前事务直接读取即可（禁止反序再加锁）
            Software locked = softwareMapper.selectById(task.getSoftwareId());
            revision.setReviewedAt(LocalDateTime.now(clock));
            revision.setReviewerId(null);
            revision.setReviewReason(truncate(reason, 1000));
            approvalService.rejectAndClone(locked, revision, 0L, "SYSTEM", "AUTO_REJECT",
                    "CHECKING", truncate(reason, 1000), null);
            return Transition.APPLIED;
        });
        if (transition == Transition.APPLIED) {
            finish(task, leaseVersion, "REJECTED", task.getAttempts(), null, null, now);
        } else if (transition == Transition.CANCELLED) {
            finish(task, leaseVersion, "CANCELLED", task.getAttempts(), null,
                    "提交记录已不在检查中", now);
        } else {
            finish(task, leaseVersion, "DEAD_LETTER", task.getAttempts(), null,
                    "冻结内容摘要不一致", now);
        }
    }

    /** 锁定快照并校验仍处于 CHECKING（软件行锁先于快照行）。 */
    private SoftwareRevision lockCheckable(SoftwareReviewCheckTask task) {
        Software locked = lockMapper.lockSoftware(task.getSoftwareId());
        if (locked == null || locked.getDeletedAt() != null) {
            return null;
        }
        SoftwareRevision revision = lockMapper.lockRevision(task.getRevisionId());
        if (revision == null || revision.getReviewStatus() != SoftwareReviewStatus.CHECKING) {
            return null;
        }
        return revision;
    }

    private boolean digestMatches(SoftwareReviewCheckTask task, SoftwareRevision revision) {
        String digest = SoftwareReviewTextDigest.digest(reviewTexts(revision));
        return digest.equals(task.getContentDigest()) && digest.equals(revision.getContentDigest());
    }

    private List<String> reviewTexts(SoftwareRevision snapshot) {
        List<SoftwareRelease> releases = releaseMapper.selectList(
                new LambdaQueryWrapper<SoftwareRelease>()
                        .eq(SoftwareRelease::getRevisionId, snapshot.getId()));
        List<SoftwareSource> sources = releases.isEmpty() ? List.of()
                : sourceMapper.selectList(new LambdaQueryWrapper<SoftwareSource>()
                        .in(SoftwareSource::getReleaseId,
                                releases.stream().map(SoftwareRelease::getId).toList()));
        return SoftwareReviewTextDigest.collect(snapshot, releases, sources);
    }

    private Boolean accountAvailable(Long ownerId) {
        var profiles = userSupportFeignClient.profiles(Map.of("userIds", List.of(ownerId))).data();
        if (profiles == null || profiles.isEmpty()) {
            return null;
        }
        return profiles.get(0).accountAvailable();
    }

    private Long ownerOf(Long softwareId) {
        Software software = softwareMapper.selectById(softwareId);
        return software == null || software.getDeletedAt() != null ? null : software.getOwnerId();
    }

    /** 临时故障：保持 CHECKING，指数退避；超过上限进入死信等待人工处置。 */
    private void retry(SoftwareReviewCheckTask task, int leaseVersion, LocalDateTime now,
                       String error) {
        int attempts = (task.getAttempts() == null ? 0 : task.getAttempts()) + 1;
        if (attempts >= MAX_ATTEMPTS) {
            finish(task, leaseVersion, "DEAD_LETTER", attempts, null,
                    truncate(error, 1024), now);
            log.error("审核检查任务进入死信 taskId={} revisionId={}: {}",
                    task.getId(), task.getRevisionId(), error);
            return;
        }
        LocalDateTime next = now.plus(backoff(attempts));
        finish(task, leaseVersion, "PENDING", attempts, next, truncate(error, 1024), now);
        log.warn("审核检查临时失败 taskId={} attempts={} 下次重试 {}: {}",
                task.getId(), attempts, next, error);
    }

    private Duration backoff(int attempts) {
        long multiplier = 1L << Math.min(Math.max(attempts - 1, 0), 6);
        Duration delay = BASE_BACKOFF.multipliedBy(multiplier);
        return delay.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : delay;
    }

    private void finish(SoftwareReviewCheckTask task, int leaseVersion, String status,
                        Integer attempts, LocalDateTime nextRetryAt, String lastError,
                        LocalDateTime now) {
        int updated = taskMapper.finish(task.getId(), status,
                attempts == null ? 0 : attempts, nextRetryAt, lastError,
                isTerminal(status, nextRetryAt) ? now : null, leaseVersion, workerId, now);
        if (updated == 0) {
            // 租约已被接管或撤回取消：新执行者/取消方持有最终状态，旧结果不得覆盖
            log.warn("检查任务回写被 fencing 拒绝 taskId={} status={}", task.getId(), status);
        }
    }

    private boolean isTerminal(String status, LocalDateTime nextRetryAt) {
        return nextRetryAt == null && !"PENDING".equals(status);
    }

    private String errorOf(RuntimeException e) {
        String message = e.getMessage();
        return e.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    private String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private enum Transition {
        APPLIED, CANCELLED, DIGEST_MISMATCH
    }
}
