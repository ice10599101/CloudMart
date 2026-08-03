package com.cloudmart.job.service.impl;

import com.cloudmart.job.entity.SysJob;
import com.cloudmart.job.entity.SysJobLog;
import com.cloudmart.job.repository.SysJobLogMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class JobInvoker {

    private final SysJobLogMapper sysJobLogMapper;

    public JobInvoker(SysJobLogMapper sysJobLogMapper) {
        this.sysJobLogMapper = sysJobLogMapper;
    }

    public void invoke(SysJob job) {
        SysJobLog log = new SysJobLog();
        log.setJobName(job.getJobName());
        log.setJobGroup(job.getJobGroup());
        log.setInvokeTarget(job.getInvokeTarget());
        log.setStartTime(LocalDateTime.now());

        try {
            executeTarget(job.getInvokeTarget());
            log.setStatus(0);
            log.setJobMessage(job.getJobName() + " 执行成功");
        } catch (Exception e) {
            log.setStatus(1);
            log.setJobMessage(job.getJobName() + " 执行失败");
            log.setExceptionInfo(e.getMessage() != null && e.getMessage().length() > 2000
                    ? e.getMessage().substring(0, 2000) : e.getMessage());
        } finally {
            log.setEndTime(LocalDateTime.now());
            sysJobLogMapper.insert(log);
        }
    }

    private void executeTarget(String invokeTarget) {
        throw new UnsupportedOperationException("任务执行目标: " + invokeTarget + " - 需要配置具体的执行逻辑");
    }
}
