package com.cloudmart.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.job.dto.SysJobLogResponse;
import com.cloudmart.job.dto.SysJobRequest;
import com.cloudmart.job.dto.SysJobResponse;
import com.cloudmart.job.entity.SysJob;
import com.cloudmart.job.entity.SysJobLog;
import com.cloudmart.job.repository.SysJobLogMapper;
import com.cloudmart.job.repository.SysJobMapper;
import com.cloudmart.job.service.SysJobService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 定时任务调度服务（T14 重构）。
 *
 * <p>状态语义统一（修复状态反转缺陷）：{@code 1=启用、0=暂停}，与后台
 * Job.tsx（0暂停/1正常）及启动恢复一致——旧实现 create/update/changeStatus 以
 * 0=调度，新建"启用"任务从不运行、新建"暂停"任务反而运行。</p>
 *
 * <p>调度应用时机：增删改在事务提交后（afterCommit）应用——旧实现事务内调度，
 * 回滚后本地调度器残留幽灵任务。</p>
 *
 * <p>多实例互斥（T14）：任务触发经 Redis 触发锁（唯一触发键
 * {@code jobId+scheduledTime}，SET NX）+ 运行锁（不并发语义，前一轮未结束
 * 下一轮跳过——"不并发、错过合并为一次"默认策略）。Redis 故障 fail-open：
 * 照常执行并告警（任务自身幂等兜底），不静默丢调度。</p>
 */
@Service
public class SysJobServiceImpl implements SysJobService {

    /** 状态：1=启用 0=暂停（T14 统一语义） */
    static final int STATUS_ENABLED = 1;
    static final int STATUS_PAUSED = 0;

    private final SysJobMapper sysJobMapper;
    private final SysJobLogMapper sysJobLogMapper;
    private final ThreadPoolTaskScheduler taskScheduler;
    private final StringRedisTemplate redisTemplate;

    private final JobInvoker jobInvoker;
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    public SysJobServiceImpl(SysJobMapper sysJobMapper, SysJobLogMapper sysJobLogMapper,
                             ThreadPoolTaskScheduler taskScheduler, JobInvoker jobInvoker,
                             StringRedisTemplate redisTemplate) {
        this.sysJobMapper = sysJobMapper;
        this.sysJobLogMapper = sysJobLogMapper;
        this.taskScheduler = taskScheduler;
        this.jobInvoker = jobInvoker;
        this.redisTemplate = redisTemplate;
    }
    /** JOB-01：启动恢复——重启后自动重新注册全部启用任务（原只在创建/修改时注册） */
    @jakarta.annotation.PostConstruct
    public void recoverEnabledJobs() {
        var enabled = sysJobMapper.selectList(
                new LambdaQueryWrapper<com.cloudmart.job.entity.SysJob>()
                        .eq(com.cloudmart.job.entity.SysJob::getStatus, 1));
        for (var job : enabled) {
            try {
                scheduleJob(job);
            } catch (Exception e) {
                // 单任务恢复失败不阻断其他任务（cron 非法等），日志可见人工修复
                org.slf4j.LoggerFactory.getLogger(SysJobServiceImpl.class)
                        .error("[JOB01] 任务恢复失败 id={} target={}: {}", job.getId(),
                                job.getInvokeTarget(), e.getMessage());
            }
        }
    }


    @Override
    public IPage<SysJobResponse> page(Integer page, Integer pageSize, String jobName, Integer status) {
        LambdaQueryWrapper<SysJob> wrapper = new LambdaQueryWrapper<>();
        if (jobName != null && !jobName.isEmpty()) {
            wrapper.like(SysJob::getJobName, jobName);
        }
        if (status != null) {
            wrapper.eq(SysJob::getStatus, status);
        }
        wrapper.orderByAsc(SysJob::getCreatedAt);
        Page<SysJob> result = sysJobMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return result.convert(this::toResponse);
    }

    @Override
    public SysJobResponse getById(Long id) {
        SysJob job = sysJobMapper.selectById(id);
        if (job == null) throw new BusinessException("JOB_NOT_FOUND", "任务不存在");
        return toResponse(job);
    }

    @Override
    @Transactional
    public Long create(SysJobRequest request) {
        validateCron(request.cronExpression());
        validatePolicy(request);
        // JOB-01：未知 handler 配置时拒绝（白名单前置校验）
        if (!jobInvoker.isRegistered(request.invokeTarget())) {
            throw new BusinessException("JOB_HANDLER_NOT_FOUND", "未注册的任务目标: " + request.invokeTarget());
        }
        SysJob job = new SysJob();
        job.setJobName(request.jobName());
        job.setJobGroup(request.jobGroup());
        job.setInvokeTarget(request.invokeTarget());
        job.setCronExpression(request.cronExpression());
        job.setMisfirePolicy(request.misfirePolicy() != null ? request.misfirePolicy() : 1);
        job.setConcurrent(request.concurrent() != null ? request.concurrent() : 1);
        job.setStatus(request.status() != null ? request.status() : STATUS_ENABLED);
        job.setRemark(request.remark());
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        sysJobMapper.insert(job);

        boolean shouldSchedule = job.getStatus() == STATUS_ENABLED;
        applyAfterCommit(() -> {
            if (shouldSchedule) {
                scheduleJob(job);
            }
        });
        return job.getId();
    }

    @Override
    @Transactional
    public void update(Long id, SysJobRequest request) {
        SysJob job = sysJobMapper.selectById(id);
        if (job == null) throw new BusinessException("JOB_NOT_FOUND", "任务不存在");
        validateCron(request.cronExpression());
        validatePolicy(request);
        // JOB-01：未知 handler 配置时拒绝（白名单前置校验）
        if (!jobInvoker.isRegistered(request.invokeTarget())) {
            throw new BusinessException("JOB_HANDLER_NOT_FOUND", "未注册的任务目标: " + request.invokeTarget());
        }

        job.setJobName(request.jobName());
        job.setJobGroup(request.jobGroup());
        job.setInvokeTarget(request.invokeTarget());
        job.setCronExpression(request.cronExpression());
        job.setMisfirePolicy(request.misfirePolicy());
        job.setConcurrent(request.concurrent());
        job.setStatus(request.status());
        job.setRemark(request.remark());
        job.setUpdatedAt(LocalDateTime.now());
        sysJobMapper.updateById(job);

        // T14：先取消本地旧调度，事务提交后按新状态重排（1=启用才调度）
        cancelJob(id);
        boolean shouldSchedule = job.getStatus() != null && job.getStatus() == STATUS_ENABLED;
        applyAfterCommit(() -> {
            if (shouldSchedule) {
                scheduleJob(job);
            }
        });
    }

    @Override
    @Transactional
    public void delete(Long id) {
        sysJobMapper.deleteById(id);
        applyAfterCommit(() -> cancelJob(id));
    }

    @Override
    @Transactional
    public void changeStatus(Long id, Integer status) {
        SysJob job = sysJobMapper.selectById(id);
        if (job == null) throw new BusinessException("JOB_NOT_FOUND", "任务不存在");
        // T14：语义修复——1=启用（调度），0=暂停（取消）；旧实现完全反转
        if (status == null || (status != STATUS_ENABLED && status != STATUS_PAUSED)) {
            throw new BusinessException("JOB_STATUS_INVALID", "任务状态非法（1=启用 0=暂停）");
        }

        boolean shouldSchedule = status == STATUS_ENABLED;
        job.setStatus(status);
        job.setUpdatedAt(LocalDateTime.now());
        sysJobMapper.updateById(job);

        applyAfterCommit(() -> {
            if (shouldSchedule) {
                scheduleJob(job);
            } else {
                cancelJob(id);
            }
        });
    }

    @Override
    public void runOnce(Long id) {
        SysJob job = sysJobMapper.selectById(id);
        if (job == null) throw new BusinessException("JOB_NOT_FOUND", "任务不存在");
        jobInvoker.invoke(job);
    }

    @Override
    public IPage<SysJobLogResponse> pageJobLogs(Long jobId, Integer page, Integer pageSize) {
        LambdaQueryWrapper<SysJobLog> wrapper = new LambdaQueryWrapper<>();
        if (jobId != null) {
            SysJob job = sysJobMapper.selectById(jobId);
            if (job != null) {
                wrapper.eq(SysJobLog::getJobName, job.getJobName());
            }
        }
        wrapper.orderByDesc(SysJobLog::getStartTime);
        Page<SysJobLog> result = sysJobLogMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return result.convert(this::toLogResponse);
    }

    @Override
    @Transactional
    public void deleteJobLog(Long id) {
        sysJobLogMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void cleanJobLogs() {
        sysJobLogMapper.delete(new LambdaQueryWrapper<>());
    }

    /** T14：策略门禁——仅支持"不并发、错过合并为一次"（misfire=1/concurrent=1），其余明确拒绝。 */
    private void validatePolicy(SysJobRequest request) {
        int misfire = request.misfirePolicy() == null ? 1 : request.misfirePolicy();
        int concurrent = request.concurrent() == null ? 1 : request.concurrent();
        if (misfire != 1 || concurrent != 1) {
            throw new BusinessException("JOB_POLICY_UNSUPPORTED",
                    "当前仅支持 misfirePolicy=1（错过合并为一次）与 concurrent=1（不并发），"
                            + "其余策略未实现，请勿配置");
        }
    }

    private void scheduleJob(SysJob job) {
        CronTrigger trigger = new CronTrigger(job.getCronExpression());
        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> invokeWithMutex(job, trigger),
                trigger
        );
        scheduledTasks.put(job.getId(), future);
    }

    /**
     * T14 多实例互斥：触发锁（jobId+scheduledTime，SET NX——两实例同一触发只有一个执行）
     * + 运行锁（不并发：上一轮仍在运行本轮跳过=错过合并）。Redis 故障 fail-open 照常执行。
     */
    private void invokeWithMutex(SysJob job, CronTrigger trigger) {
        String slot = resolveScheduledSlot(trigger);
        String triggerLockKey = "lock:job:trigger:" + job.getId() + ":" + slot;
        String runLockKey = "lock:job:running:" + job.getId();
        try {
            Boolean wonSlot = redisTemplate.opsForValue()
                    .setIfAbsent(triggerLockKey, instanceFingerprint(), Duration.ofSeconds(90));
            if (!Boolean.TRUE.equals(wonSlot)) {
                // 同一触发已被其他实例消费
                return;
            }
            Boolean wonRun = redisTemplate.opsForValue()
                    .setIfAbsent(runLockKey, instanceFingerprint(), Duration.ofMinutes(30));
            if (!Boolean.TRUE.equals(wonRun)) {
                // 不并发：上一轮仍在运行，本轮跳过
                return;
            }
            try {
                jobInvoker.invoke(job);
            } finally {
                redisTemplate.delete(runLockKey);
            }
        } catch (Exception ex) {
            // fail-open：Redis 不可用照常执行（任务自身幂等兜底），告警可见
            org.slf4j.LoggerFactory.getLogger(SysJobServiceImpl.class)
                    .warn("[T14] 触发锁不可用（fail-open 执行） jobId={}: {}", job.getId(), ex.getMessage());
            jobInvoker.invoke(job);
        }
    }

    /** 触发槽位：以"上一秒为最近执行"反推本次计划触发时间（秒级偏差下的公共槽位）。 */
    private String resolveScheduledSlot(CronTrigger trigger) {
        try {
            java.time.Instant recent = java.time.Instant.now().minusSeconds(1);
            var ctx = new org.springframework.scheduling.support.SimpleTriggerContext(recent, recent, recent);
            ZonedDateTime slot = trigger.nextExecution(ctx)
                    .atZone(ZoneOffset.UTC);
            return String.valueOf(slot.toInstant().getEpochSecond());
        } catch (Exception ex) {
            return String.valueOf(System.currentTimeMillis() / 1000);
        }
    }

    private String instanceFingerprint() {
        return java.util.UUID.randomUUID().toString();
    }

    /** T14：调度变更在事务提交后应用（无事务上下文时立即执行，兼容测试）。 */
    private void applyAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private void cancelJob(Long id) {
        ScheduledFuture<?> future = scheduledTasks.remove(id);
        if (future != null) {
            future.cancel(false);
        }
    }

    private void validateCron(String cron) {
        try {
            new CronTrigger(cron);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("CRON_INVALID", "Cron表达式不正确: " + e.getMessage());
        }
    }

    private SysJobResponse toResponse(SysJob job) {
        return new SysJobResponse(
                job.getId(), job.getJobName(), job.getJobGroup(),
                job.getInvokeTarget(), job.getCronExpression(),
                job.getMisfirePolicy(), job.getConcurrent(), job.getStatus(),
                job.getRemark(), job.getCreatedAt(), null
        );
    }

    private SysJobLogResponse toLogResponse(SysJobLog log) {
        String duration = "";
        if (log.getStartTime() != null && log.getEndTime() != null) {
            long ms = java.time.Duration.between(log.getStartTime(), log.getEndTime()).toMillis();
            duration = ms + " ms";
        }
        return new SysJobLogResponse(
                log.getId(), null, log.getJobName(), log.getJobGroup(),
                log.getInvokeTarget(), null, log.getJobMessage(), log.getStatus(),
                log.getExceptionInfo(), log.getStartTime(), log.getEndTime(), duration
        );
    }
}
