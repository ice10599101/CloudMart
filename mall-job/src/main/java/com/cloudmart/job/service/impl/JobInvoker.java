package com.cloudmart.job.service.impl;

import com.cloudmart.job.entity.SysJob;
import com.cloudmart.job.entity.SysJobLog;
import com.cloudmart.job.repository.SysJobLogMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class JobInvoker {

    private final SysJobLogMapper sysJobLogMapper;
    private final com.cloudmart.job.handler.BusinessJobHandler businessJobHandler;

    /**
     * JOB-01：白名单 handler registry——invokeTarget 只允许已知任务名
     * （与 XXL-JOB handler 同一套业务动作），不执行任意类名/表达式；
     * 未知配置在调度与「立即执行」时都被拒绝。
     */
    private final java.util.Map<String, Runnable> handlerRegistry;

    public JobInvoker(SysJobLogMapper sysJobLogMapper,
                      com.cloudmart.job.handler.BusinessJobHandler businessJobHandler) {
        this.sysJobLogMapper = sysJobLogMapper;
        this.businessJobHandler = businessJobHandler;
        this.handlerRegistry = java.util.Map.ofEntries(
                java.util.Map.entry("groupExpirationHandler", (Runnable) businessJobHandler::groupExpirationHandler),
                java.util.Map.entry("orderTimeoutCancelHandler", (Runnable) businessJobHandler::orderTimeoutCancelHandler),
                java.util.Map.entry("couponExpirationHandler", (Runnable) businessJobHandler::couponExpirationHandler),
                java.util.Map.entry("treeMoodScanHandler", (Runnable) businessJobHandler::treeMoodScanHandler),
                java.util.Map.entry("wishOverdueScanHandler", (Runnable) businessJobHandler::wishOverdueScanHandler),
                java.util.Map.entry("homeHotCacheRefreshHandler", (Runnable) businessJobHandler::homeHotCacheRefreshHandler),
                java.util.Map.entry("seasonScanHandler", (Runnable) businessJobHandler::seasonScanHandler),
                java.util.Map.entry("badgeCompensationScanHandler", (Runnable) businessJobHandler::badgeCompensationScanHandler),
                java.util.Map.entry("capsuleOpenScanHandler", (Runnable) businessJobHandler::capsuleOpenScanHandler),
                java.util.Map.entry("aiReminderScanHandler", (Runnable) businessJobHandler::aiReminderScanHandler),
                java.util.Map.entry("leaderboardRefreshHandler", (Runnable) businessJobHandler::leaderboardRefreshHandler),
                java.util.Map.entry("encounterMatchHandler", (Runnable) businessJobHandler::encounterMatchHandler),
                java.util.Map.entry("traceCleanupHandler", (Runnable) businessJobHandler::traceCleanupHandler),
                java.util.Map.entry("starlightDecayHandler", (Runnable) businessJobHandler::starlightDecayHandler),
                java.util.Map.entry("starlightReconcileHandler", (Runnable) businessJobHandler::starlightReconcileHandler),
                java.util.Map.entry("levelUpgradeHandler", (Runnable) businessJobHandler::levelUpgradeHandler),
                java.util.Map.entry("restrictionReleaseHandler", (Runnable) businessJobHandler::restrictionReleaseHandler),
                java.util.Map.entry("riskScoreDecayHandler", (Runnable) businessJobHandler::riskScoreDecayHandler),
                java.util.Map.entry("inactiveArchiveHandler", (Runnable) businessJobHandler::inactiveArchiveHandler),
                java.util.Map.entry("dataExportPurgeHandler", (Runnable) businessJobHandler::dataExportPurgeHandler),
                java.util.Map.entry("accountDeletionScanHandler", (Runnable) businessJobHandler::accountDeletionScanHandler),
                java.util.Map.entry("activityRewardCheckHandler", (Runnable) businessJobHandler::activityRewardCheckHandler),
                java.util.Map.entry("brandRewardCheckHandler", (Runnable) businessJobHandler::brandRewardCheckHandler)
        );
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
        Runnable handler = handlerRegistry.get(invokeTarget);
        if (handler == null) {
            // JOB-01：未知 handler 配置时拒绝——绝不通过反射执行任意目标
            throw new com.cloudmart.common.exception.BusinessException(
                    "JOB_HANDLER_NOT_FOUND", "未注册的任务目标: " + invokeTarget);
        }
        handler.run();
    }

    /** invokeTarget 是否在白名单内（创建/修改任务时前置校验用） */
    public boolean isRegistered(String invokeTarget) {
        return invokeTarget != null && handlerRegistry.containsKey(invokeTarget);
    }
}
