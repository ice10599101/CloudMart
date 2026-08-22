package com.cloudmart.wish.service;

import com.cloudmart.wish.enums.TreeSeason;
import com.cloudmart.wish.vo.EnvConfigVO;
import com.cloudmart.wish.vo.SpecialEventVO;
import com.cloudmart.wish.vo.TreeEnvVO;

import java.util.List;

/**
 * 生命树环境服务（文档 2.2 气象情绪联动 / Sprint 2.2 动态环境扩展）。
 *
 * <p>Sprint 2.2 多维环境模型：情绪环境（Sprint 1.5 状态机）+ 季节
 * （mall-job 每日落库）+ 真实天气（和风天气 API）+ 时段（客户端时区）+
 * 特殊事件（管理员触发）。</p>
 */
public interface TreeEnvService {

    /**
     * 查询当前环境状态（四端环境渲染数据源）。
     *
     * <p>moodScore 读取 Redis 聚合缓存（TTL 10 分钟），Redis 不可用时
     * 降级返回 null（Fail-Open，环境状态本身以 DB 为准）。</p>
     *
     * @param tzOffsetMinutes 客户端 UTC 时区偏移分钟（东八区=480；
     *                        null/0=UTC；时段按用户本地时区计算，文档 Sprint 2.2 验收）
     */
    TreeEnvVO getCurrentEnv(Integer tzOffsetMinutes);

    /**
     * 执行一次情绪扫描（mall-job 每 5 分钟经内部接口触发）。
     *
     * <p>链路：滑动窗口情绪聚合 → BLESS 突增检测 → 状态机流转 →
     * 单行状态表更新 + Redis mood 缓存刷新。多实例并发由 Redis 锁互斥，
     * 拿不到锁时直接返回当前状态（幂等）。</p>
     */
    TreeEnvVO scan();

    /**
     * 季节落库扫描（mall-job 每日 00:00 经内部接口触发，文档 Sprint 2.2：
     * 按日期判定写入 wish_world_tree_state.season）。
     *
     * <p>幂等：季节未变化不产生写操作；单行表无并发风险。</p>
     *
     * @return 本次扫描判定的季节
     */
    TreeSeason scanSeason();

    /**
     * 查询当前活跃特殊事件（惰性过期判定：expires_at 已过视同结束）。
     *
     * @return 活跃事件；无事件或已过期返回 null
     */
    SpecialEventVO getActiveSpecialEvent();

    /**
     * 查询已启用的环境配置（公开，四端渲染参数数据源）。
     *
     * @return 按 priority 降序的启用配置列表
     */
    List<EnvConfigVO> listActiveEnvConfigs();
}
