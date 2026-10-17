package com.cloudmart.job.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.job.handler.BusinessJobHandler;
import com.cloudmart.job.repository.XxlJobQueryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OPS-01：调度适配层（只读 XXL 事实 + 白名单手动运行）。
 *
 * <p>调度唯一事实来源是 XXL-JOB：</p>
 * <ul>
 *   <li>任务配置/执行日志 = {@code xxl_job.xxl_job_info / xxl_job_log} 只读适配展示，
 *       生命周期操作走 XXL 调度中心控制台（本服务不复制 Cron 注册表）；</li>
 *   <li>手动运行走同一 executor 的白名单 handler（{@link BusinessJobHandler}），
 *       与计划触发同路径、同业务幂等事实，绝不反射执行任意类名；</li>
 *   <li>原本地 Cron 注册表（sys_job/scheduledTasks/invokeWithMutex）已删除，
 *       不并行维持双套；业务服务的进程内恢复扫描由各业务服务自持并标注
 *       LOCAL_RECOVERY 来源，不与本服务计划执行混淆。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class XxlJobAdapterService {

    private final XxlJobQueryMapper xxlJobQueryMapper;
    private final JobInvoker jobInvoker;

    /** 任务列表（XXL 配置事实：handler/Cron/状态/负责人） */
    public Map<String, Object> page(Integer page, Integer pageSize, String jobDesc) {
        int p = Math.max(1, page);
        int size = Math.min(Math.max(1, pageSize), 100);
        List<Map<String, Object>> records =
                xxlJobQueryMapper.pageJobs(blankToNull(jobDesc), (p - 1) * size, size);
        long total = xxlJobQueryMapper.countJobs(blankToNull(jobDesc));
        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("current", p);
        result.put("size", size);
        return result;
    }

    /** 任务详情 */
    public Map<String, Object> getById(long id) {
        Map<String, Object> job = xxlJobQueryMapper.jobById(id);
        if (job == null) {
            throw new BusinessException("JOB_NOT_FOUND", "XXL 任务不存在: " + id);
        }
        return job;
    }

    /**
     * 手动运行：从 XXL 事实取 handler 名，经白名单直接执行
     * （与计划触发同一业务动作、同一幂等事实；XXL 调度中心"执行一次"亦可）。
     */
    public void runOnce(long id) {
        String handler = xxlJobQueryMapper.handlerById(id);
        if (handler == null || handler.isBlank()) {
            throw new BusinessException("JOB_HANDLER_EMPTY", "任务未配置 executor_handler（空 bean 任务不可运行）");
        }
        jobInvoker.invokeByName(handler, "XXL#" + id);
    }

    /** 执行日志（XXL 事实：触发/执行结果码与消息） */
    public Map<String, Object> pageLogs(long jobId, Integer page, Integer pageSize) {
        int p = Math.max(1, page);
        int size = Math.min(Math.max(1, pageSize), 100);
        List<Map<String, Object>> records = xxlJobQueryMapper.pageLogs(jobId, (p - 1) * size, size);
        long total = xxlJobQueryMapper.countLogs(jobId);
        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("current", p);
        result.put("size", size);
        return result;
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
