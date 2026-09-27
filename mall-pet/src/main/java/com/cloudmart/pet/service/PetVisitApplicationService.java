package com.cloudmart.pet.service;

import com.cloudmart.pet.entity.PetVisitFact;

/**
 * 拜访统一应用服务（B01/BE-06，§3.3-5）。
 *
 * <p>邻居串门、家园拜访、好友互访三类入口共享同一数据库事实与额度：</p>
 * <ul>
 *   <li>唯一事实 {@code (visitorUserId, ownerUserId, businessDate)}——同主人同业务日
 *       至多一次（不同宠物同主人仍只算一次）；重复拜访返回 DUPLICATE（冷却拒绝）；</li>
 *   <li>收益资格由 {@link PetQuotaService}（数据库权威额度）裁决——
 *       每日收益上限共享（默认 10），好友互访入口另有更严的收益上限（默认 5）；
 *       <b>Redis 故障不改变有收益资格</b>（Redis 不再参与判定）；</li>
 *   <li>超过收益额度的拜访仍成立（无收益：精力/心情照常，下游任务/关系/成就不推进）；
 *       业务校验失败由调用方显式 release 额度（与既有额度服务约定一致）。</li>
 * </ul>
 */
public interface PetVisitApplicationService {

    /**
     * 记录拜访事实并裁决收益资格（加入调用方事务；先锁访问者 user guard）。
     *
     * @return factCreated=false 表示同主人同业务日已拜访过（调用方按冷却拒绝）
     */
    VisitGrant recordVisit(Long visitorUserId, Long ownerUserId,
                           Long visitorPetId, Long ownerPetId, VisitSource source);

    /** @return 访问者今日是否已拜访过该主人（展示用，读库） */
    boolean visitedToday(Long visitorUserId, Long ownerUserId);

    /** 拜访入口 */
    enum VisitSource { NEIGHBOR, ROOM, FRIEND }

    /** 记录结果 */
    record VisitGrant(boolean factCreated, boolean rewardGranted) {
    }
}
