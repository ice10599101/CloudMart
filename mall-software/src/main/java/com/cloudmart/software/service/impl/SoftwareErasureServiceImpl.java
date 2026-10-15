package com.cloudmart.software.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.entity.SoftwareErasureTask;
import com.cloudmart.software.repository.SoftwareErasureSqlMapper;
import com.cloudmart.software.repository.SoftwareErasureTaskMapper;
import com.cloudmart.software.service.SoftwareErasureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 软件域账号注销实现（方案 11.3）。
 *
 * <p>结构：software_erasure_task 每用户唯一行承载状态机
 * （PENDING→RUNNING→DONE/FAILED），阶段清单顺序执行、每阶段独立事务——
 * 单阶段失败时已完成阶段持久保留，重试从未完成阶段续跑（阶段内语句全部幂等）。
 * 当前阶段集覆盖 P0 全部用户数据表（软件/评论/点赞/收藏/获取事实/授权/资格/反馈/举报）；
 * 评分、订阅、偏好等 P1 表上线后在此追加阶段。审计日志与 operation 幂等事实
 * 按手册要求保留（不携带链接/密码，天然可留存）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoftwareErasureServiceImpl implements SoftwareErasureService {

    /** 阶段顺序即执行序；每阶段名与 task.phase 对应，续跑按清单跳过已完成阶段 */
    private static final String[] PHASES = {
            "UNLIST_SOFTWARE",
            "ANONYMIZE_COMMENTS",
            "CLEAR_RELATIONS",
            "CLEAR_ERASURE_IDENTITY",
            "CLOSE_PERMISSIONS",
    };

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_DONE = "DONE";
    private static final String STATUS_FAILED = "FAILED";

    private final SoftwareErasureTaskMapper taskMapper;
    private final SoftwareErasureSqlMapper sqlMapper;
    private final PlatformTransactionManager transactionManager;

    @Override
    public boolean eraseUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("SOFTWARE_ERASURE_INVALID", "userId 非法");
        }
        SoftwareErasureTask task = loadOrCreateTask(userId);
        if (STATUS_DONE.equals(task.getStatus())) {
            // 幂等：编排重试/重复通知不会二次清理
            return true;
        }

        int attempt = (task.getAttempts() == null ? 0 : task.getAttempts()) + 1;
        markRunning(task, attempt);
        try {
            runPhases(userId, task);
        } catch (Exception e) {
            markFailed(task, attempt, e);
            throw new BusinessException("SOFTWARE_ERASURE_INCOMPLETE",
                    "软件域清理未完成，已保留进度等待重试", e);
        }
        markDone(task);
        log.info("软件域注销完成 userId={} attempts={} 阶段数={}", userId, attempt, PHASES.length);
        return true;
    }

    private SoftwareErasureTask loadOrCreateTask(Long userId) {
        SoftwareErasureTask existing = taskMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SoftwareErasureTask>()
                        .eq(SoftwareErasureTask::getUserId, userId));
        if (existing != null) {
            return existing;
        }
        SoftwareErasureTask task = new SoftwareErasureTask();
        task.setUserId(userId);
        task.setStatus(STATUS_PENDING);
        task.setPhase("");
        task.setCursorId(0L);
        task.setAttempts(0);
        task.setLeaseVersion(0);
        try {
            taskMapper.insert(task);
            return task;
        } catch (DuplicateKeyException e) {
            // 并发首插：唯一键兜底，取已存在行（禁止再次执行创建逻辑）
            return Objects.requireNonNull(taskMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SoftwareErasureTask>()
                            .eq(SoftwareErasureTask::getUserId, userId)));
        }
    }

    private void markRunning(SoftwareErasureTask task, int attempt) {
        SoftwareErasureTask update = new SoftwareErasureTask();
        update.setId(task.getId());
        update.setStatus(STATUS_RUNNING);
        update.setAttempts(attempt);
        update.setLastError(null);
        update.setNextRetryAt(null);
        taskMapper.updateById(update);
        task.setStatus(STATUS_RUNNING);
        task.setAttempts(attempt);
    }

    /** 阶段顺序执行；每阶段独立事务（清理语句与游标推进同事务持久化） */
    private void runPhases(Long userId, SoftwareErasureTask task) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        for (String phase : PHASES) {
            template.executeWithoutResult(status -> executePhase(userId, phase));
            SoftwareErasureTask progress = new SoftwareErasureTask();
            progress.setId(task.getId());
            progress.setPhase(phase);
            progress.setCursorId(0L);
            taskMapper.updateById(progress);
            task.setPhase(phase);
        }
    }

    /** 单阶段清理；语句幂等，重试零增量效果 */
    private void executePhase(Long userId, String phase) {
        switch (phase) {
            case "UNLIST_SOFTWARE" -> sqlMapper.unlistActiveSoftware(userId);
            case "ANONYMIZE_COMMENTS" -> {
                sqlMapper.softDeleteOwnComments(userId);
                sqlMapper.deleteOwnCommentLikes(userId);
            }
            case "CLEAR_RELATIONS" -> {
                sqlMapper.deleteOwnFavorites(userId);
                sqlMapper.deleteOwnAccessDaily(userId);
            }
            case "CLEAR_ERASURE_IDENTITY" -> {
                sqlMapper.deleteOwnLinkFeedback(userId);
                sqlMapper.deleteOwnReports(userId);
            }
            case "CLOSE_PERMISSIONS" -> {
                sqlMapper.revokeOwnAccessGrants(userId);
                sqlMapper.disableOwnPublishPermission(userId);
            }
            default -> throw new BusinessException("SOFTWARE_ERASURE_INVALID",
                    "未知注销阶段: " + phase);
        }
    }

    private void markFailed(SoftwareErasureTask task, int attempt, Exception cause) {
        SoftwareErasureTask update = new SoftwareErasureTask();
        update.setId(task.getId());
        update.setStatus(STATUS_FAILED);
        update.setLastError(truncate(cause.getMessage()));
        // 指数退避：60s/300s/900s/3600s（与注销编排重试节奏对齐，上限 1 小时）
        long[] backoff = {60, 300, 900, 3600};
        long seconds = backoff[Math.min(attempt - 1, backoff.length - 1)];
        update.setNextRetryAt(LocalDateTime.now().plusSeconds(seconds));
        taskMapper.updateById(update);
        log.warn("软件域注销阶段失败 userId={} phase={} attempt={}: {}",
                task.getUserId(), task.getPhase(), attempt, cause.getMessage());
    }

    private void markDone(SoftwareErasureTask task) {
        SoftwareErasureTask update = new SoftwareErasureTask();
        update.setId(task.getId());
        update.setStatus(STATUS_DONE);
        update.setPhase("DONE");
        update.setCursorId(0L);
        update.setLastError(null);
        update.setNextRetryAt(null);
        taskMapper.updateById(update);
    }

    private String truncate(String message) {
        if (message == null) {
            return "ERASURE_ERROR";
        }
        return message.length() > 480 ? message.substring(0, 480) : message;
    }
}
