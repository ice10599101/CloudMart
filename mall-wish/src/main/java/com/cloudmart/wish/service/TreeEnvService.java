package com.cloudmart.wish.service;

import com.cloudmart.wish.vo.TreeEnvVO;

/**
 * 生命树情绪环境联动服务（文档 2.2 气象情绪联动 / Sprint 2.2）。
 */
public interface TreeEnvService {

    /**
     * 查询当前环境状态（四端环境渲染数据源）。
     *
     * <p>moodScore 读取 Redis 聚合缓存（TTL 10 分钟），Redis 不可用时
     * 降级返回 null（Fail-Open，环境状态本身以 DB 为准）。</p>
     */
    TreeEnvVO getCurrentEnv();

    /**
     * 执行一次情绪扫描（mall-job 每 5 分钟经内部接口触发）。
     *
     * <p>链路：滑动窗口情绪聚合 → BLESS 突增检测 → 状态机流转 →
     * 单行状态表更新 + Redis mood 缓存刷新。多实例并发由 Redis 锁互斥，
     * 拿不到锁时直接返回当前状态（幂等）。</p>
     */
    TreeEnvVO scan();
}
