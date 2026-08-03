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
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
public class SysJobServiceImpl implements SysJobService {

    private final SysJobMapper sysJobMapper;
    private final SysJobLogMapper sysJobLogMapper;
    private final ThreadPoolTaskScheduler taskScheduler;
    private final JobInvoker jobInvoker;
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    public SysJobServiceImpl(SysJobMapper sysJobMapper, SysJobLogMapper sysJobLogMapper,
                             ThreadPoolTaskScheduler taskScheduler, JobInvoker jobInvoker) {
        this.sysJobMapper = sysJobMapper;
        this.sysJobLogMapper = sysJobLogMapper;
        this.taskScheduler = taskScheduler;
        this.jobInvoker = jobInvoker;
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
        SysJob job = new SysJob();
        job.setJobName(request.jobName());
        job.setJobGroup(request.jobGroup());
        job.setInvokeTarget(request.invokeTarget());
        job.setCronExpression(request.cronExpression());
        job.setMisfirePolicy(request.misfirePolicy() != null ? request.misfirePolicy() : 1);
        job.setConcurrent(request.concurrent() != null ? request.concurrent() : 1);
        job.setStatus(request.status() != null ? request.status() : 0);
        job.setRemark(request.remark());
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        sysJobMapper.insert(job);

        if (job.getStatus() == 0) {
            scheduleJob(job);
        }
        return job.getId();
    }

    @Override
    @Transactional
    public void update(Long id, SysJobRequest request) {
        SysJob job = sysJobMapper.selectById(id);
        if (job == null) throw new BusinessException("JOB_NOT_FOUND", "任务不存在");
        validateCron(request.cronExpression());

        cancelJob(id);
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

        if (job.getStatus() == 0) {
            scheduleJob(job);
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        cancelJob(id);
        sysJobMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void changeStatus(Long id, Integer status) {
        SysJob job = sysJobMapper.selectById(id);
        if (job == null) throw new BusinessException("JOB_NOT_FOUND", "任务不存在");

        if (status == 0) {
            scheduleJob(job);
        } else {
            cancelJob(id);
        }
        job.setStatus(status);
        job.setUpdatedAt(LocalDateTime.now());
        sysJobMapper.updateById(job);
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

    private void scheduleJob(SysJob job) {
        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> jobInvoker.invoke(job),
                new CronTrigger(job.getCronExpression())
        );
        scheduledTasks.put(job.getId(), future);
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
