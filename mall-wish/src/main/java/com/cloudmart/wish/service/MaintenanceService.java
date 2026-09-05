package com.cloudmart.wish.service;

/**
 * Phase 1 运维类定时任务服务（文档 9.1，四AB 审计 P0-4）。
 *
 * <p>全部幂等 + 分批 500；经 InternalJobController 由 XXL-Job 触发
 * （任务通用要求：开始/结束/处理数日志、失败重试由 XXL-Job 重试策略承担）。</p>
 */
public interface MaintenanceService {

    /** 星光衰减：last_active_at > 30 天的用户余额 -2（最低 10），写 DECAY 流水 */
    MapResult starlightDecay();

    /** 星光对账：starlight_balance 与流水求和不一致 → 以流水为准修正 + 告警日志 */
    MapResult starlightReconcile();

    /** 等级升级：按 6.5 晋级条件表扫描，只升不降（同步 highest_level） */
    MapResult levelUpgrade();

    /** 限制解除：restricted_until < NOW 且 is_restricted=1 → 解除（risk_score 不清零） */
    MapResult restrictionRelease();

    /** 风控分衰减：无 30 天内新驳回记录的用户 risk_score -1（最低 0） */
    MapResult riskScoreDecay();

    /** 不活跃归档：last_active_at < 365 天用户的 PRIVATE/TREE_HOLE 心愿 → ARCHIVED */
    MapResult inactiveArchive();

    /** 任务执行结果（日志与接口返回用） */
    record MapResult(String task, long processed, long failed, String detail) {}
}
