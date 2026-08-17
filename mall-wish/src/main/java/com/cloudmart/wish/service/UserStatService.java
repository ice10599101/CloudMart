package com.cloudmart.wish.service;

/**
 * 用户心愿统计服务接口。
 *
 * <p>维护 {@code wish_user_stat} 表的聚合数据，避免实时 count。
 * 所有方法必须在调用方的事务上下文中执行（同事务保证一致性）。</p>
 *
 * <p>统计规则（对应文档 6.5 等级机制）：</p>
 * <ul>
 *   <li>{@code totalWishes}：累计创建心愿数（含已软删，只增不减）</li>
 *   <li>{@code activeWishes}：当前有效心愿数（创建 +1，软删 -1）</li>
 *   <li>{@code totalFulfilled}：累计还愿数（历史事实，不回退）</li>
 * </ul>
 */
public interface UserStatService {

    /**
     * 初始化用户统计记录（用户首次创建心愿时调用）。
     * 若记录已存在则跳过（幂等）。
     *
     * @param userId 用户 ID
     */
    void initUserStat(Long userId);

    /**
     * 心愿创建时：totalWishes + 1，activeWishes + 1，更新 lastActiveAt。
     * 必须与 wish insert 同事务。
     *
     * @param userId 用户 ID
     */
    void incrementOnWishCreated(Long userId);

    /**
     * 心愿软删时：activeWishes - 1（totalWishes 不变，累计值永不回退）。
     * 必须与 wish 软删同事务。
     *
     * @param userId 用户 ID
     */
    void decrementOnWishDeleted(Long userId);
}
