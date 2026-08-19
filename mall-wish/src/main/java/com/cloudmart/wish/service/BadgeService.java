package com.cloudmart.wish.service;

import com.cloudmart.wish.entity.WishBadge;
import com.cloudmart.wish.vo.BadgeDefinitionVO;
import com.cloudmart.wish.vo.BadgeWallItemVO;

import java.util.List;

/**
 * 徽章服务接口（文档 6.5 成就框架 / 2.6 / 2.9 API 契约）。
 *
 * <p>触发模型：统计变更点同步判定（心愿创建同事务 / total_helped MQ 消费事务），
 * 授予幂等由 {@code uk_user_badge(user_id, badge_id)} 唯一索引兜底；
 * 漏发补偿扫描（mall-job）属待办 ③ XXL-Job 补全范围。</p>
 */
public interface BadgeService {

    /**
     * 判定并授予达标徽章（统计变更后调用，调用方事务内执行）。
     *
     * <p>幂等：已持有跳过；并发授予由唯一索引冲突兜底。
     * 单个徽章 condition 非法时跳过该徽章不阻断（Fail-Open）。</p>
     *
     * @param userId 用户 ID
     * @return 本次新授予的徽章（调用方记日志 / 后续接 BADGE_EARNED 通知）
     */
    List<WishBadge> evaluateAndAward(Long userId);

    /**
     * 徽章墙聚合视图：全部定义 + earned/earnedAt/condition/progress。
     *
     * @param userId 用户 ID
     * @return 全部徽章条目（已获得在前按获得时间倒序，未获得按 badgeId 升序）
     */
    List<BadgeWallItemVO> getBadgeWall(Long userId);

    /**
     * 徽章图鉴（公开，无需登录）。
     *
     * @return 全部徽章定义
     */
    List<BadgeDefinitionVO> getDefinitions();

    /**
     * 漏发补偿扫描：分批遍历全部用户统计记录，逐用户执行 {@link #evaluateAndAward}。
     *
     * <p>补偿场景：total_helped 经 MQ 异步累加，消费失败重试耗尽进 DLQ 时
     * 徽章判定被跳过（漏发）。本扫描由 mall-job 每日低峰触发兜底；
     * 授予幂等（已持有跳过 + 唯一索引），重复扫描无副作用。</p>
     *
     * <p>事务边界：本方法不开整体事务；内部自调用 evaluateAndAward 不经
     * 事务代理，授予 INSERT 逐条自动提交（避免全量用户大事务；幂等由
     * uk_user_badge 唯一索引 + 已持有跳过保证）。</p>
     *
     * @return 扫描结果（扫描用户数 / 本次补授徽章数）
     */
    CompensationResult compensationScan();

    /**
     * 补偿扫描结果。
     */
    record CompensationResult(int scannedUsers, int awardedBadges) {}
}
