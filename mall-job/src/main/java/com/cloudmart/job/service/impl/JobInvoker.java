package com.cloudmart.job.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.job.handler.BusinessJobHandler;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OPS-01：白名单 handler registry——invokeTarget 只允许已知任务名
 * （与 XXL-JOB handler 同一套业务动作），不执行任意类名/表达式；
 * 未知配置在调度与「立即执行」时都被拒绝。
 * 原本地 Scheduler/Future 注册表已删除（OPS-01 双轨拆除）——本类只承载
 * 手动运行与白名单校验，计划触发由 XXL-JOB executor 直达 handler。
 */
@Component
public class JobInvoker {

    private final BusinessJobHandler businessJobHandler;

    private final Map<String, Runnable> handlerRegistry;

    public JobInvoker(BusinessJobHandler businessJobHandler) {
        this.businessJobHandler = businessJobHandler;
        this.handlerRegistry = new LinkedHashMap<>();
        put("groupExpirationHandler", businessJobHandler::groupExpirationHandler);
        put("orderAutoConfirmHandler", businessJobHandler::orderAutoConfirmHandler);
        put("orderTimeoutCancelHandler", businessJobHandler::orderTimeoutCancelHandler);
        put("couponExpirationHandler", businessJobHandler::couponExpirationHandler);
        put("treeMoodScanHandler", businessJobHandler::treeMoodScanHandler);
        put("wishOverdueScanHandler", businessJobHandler::wishOverdueScanHandler);
        put("homeHotCacheRefreshHandler", businessJobHandler::homeHotCacheRefreshHandler);
        put("seasonScanHandler", businessJobHandler::seasonScanHandler);
        put("badgeCompensationScanHandler", businessJobHandler::badgeCompensationScanHandler);
        put("capsuleOpenScanHandler", businessJobHandler::capsuleOpenScanHandler);
        put("aiReminderScanHandler", businessJobHandler::aiReminderScanHandler);
        put("leaderboardRefreshHandler", businessJobHandler::leaderboardRefreshHandler);
        put("encounterMatchHandler", businessJobHandler::encounterMatchHandler);
        put("traceCleanupHandler", businessJobHandler::traceCleanupHandler);
        put("starlightDecayHandler", businessJobHandler::starlightDecayHandler);
        put("starlightReconcileHandler", businessJobHandler::starlightReconcileHandler);
        put("levelUpgradeHandler", businessJobHandler::levelUpgradeHandler);
        put("restrictionReleaseHandler", businessJobHandler::restrictionReleaseHandler);
        put("riskScoreDecayHandler", businessJobHandler::riskScoreDecayHandler);
        put("inactiveArchiveHandler", businessJobHandler::inactiveArchiveHandler);
        put("dataExportPurgeHandler", businessJobHandler::dataExportPurgeHandler);
        put("accountDeletionScanHandler", businessJobHandler::accountDeletionScanHandler);
        put("activityRewardCheckHandler", businessJobHandler::activityRewardCheckHandler);
        put("brandRewardCheckHandler", businessJobHandler::brandRewardCheckHandler);
    }

    private void put(String name, Runnable handler) {
        handlerRegistry.put(name, handler);
    }

    /** 按白名单名执行（手动运行与计划触发同一业务动作） */
    public void invokeByName(String handler, String source) {
        Runnable target = handlerRegistry.get(handler);
        if (target == null) {
            // OPS-01：未知 handler 拒绝——绝不反射执行任意目标
            throw new BusinessException("JOB_HANDLER_NOT_FOUND",
                    "未注册的任务目标: " + handler + " (source=" + source + ")");
        }
        target.run();
    }

    /** invokeTarget 是否在白名单内 */
    public boolean isRegistered(String invokeTarget) {
        return invokeTarget != null && handlerRegistry.containsKey(invokeTarget);
    }
}
