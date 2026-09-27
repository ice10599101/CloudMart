package com.cloudmart.common.async.compensation;

/**
 * 补偿动作处理器（ASYNC-01）：模块按动作名注册（Spring Bean 即自动注册），
 * 例如 stock-confirm / stock-release / coupon-return。
 */
public interface CompensationHandler {

    /** 动作标识（机器可读，与 createIfAbsent 的 action 对应） */
    String action();

    /**
     * 执行补偿动作；失败抛出任意异常，由补偿调度器记录退避重试。
     *
     * @param aggregateId 聚合根标识（如订单号）
     * @param payload     任务载荷 JSON（登记时写入）
     */
    void execute(String aggregateId, String payload);
}
