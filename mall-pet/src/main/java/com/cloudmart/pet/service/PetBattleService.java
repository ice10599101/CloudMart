package com.cloudmart.pet.service;

import com.cloudmart.pet.dto.ChallengeBattleRequest;
import com.cloudmart.pet.vo.PetBattleVO;
import com.cloudmart.pet.vo.PetOpponentVO;

import java.util.List;

/**
 * 宠物对战服务（异步回合制，服务端全权计算——原文档 §13/§79 分阶段）。
 */
public interface PetBattleService {

    /** 对战候选：PvE 野生宠物模板 3 只 + PvP 其他用户公开宠物（等级 ±5 优先，随机 8 只） */
    List<PetOpponentVO> listOpponents(Long userId);

    /**
     * 发起挑战。PvE 立即结算返回回合流水；PvP 快照双方属性落 PENDING，
     * 防守方 accept 后才计算（MQ 通知防守方"有人向我发起挑战啦"）。
     */
    PetBattleVO challenge(Long userId, ChallengeBattleRequest request);

    /** 防守方接受挑战：按快照 + seed 计算 → FINISHED，双方发奖（MQ 通知挑战方结果） */
    PetBattleVO accept(Long userId, Long battleId);

    /** 防守方拒绝挑战：PENDING → DECLINED（MQ 通知挑战方） */
    PetBattleVO decline(Long userId, Long battleId);

    /** 对战详情（仅双方参与者可见） */
    PetBattleVO get(Long userId, Long battleId);

    /** 我的对战历史（offset 分页） */
    List<PetBattleVO> history(Long userId, int page, int pageSize);

    /** 过期未应战挑战（供定时器调用）：PENDING 超 48h → EXPIRED */
    int expirePendingBattles();
}
