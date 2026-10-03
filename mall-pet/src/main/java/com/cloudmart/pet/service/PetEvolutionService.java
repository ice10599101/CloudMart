package com.cloudmart.pet.service;

import com.cloudmart.pet.vo.PetEvolutionVO;

/**
 * 宠物进化（原文档 §89 宠物进化）。
 *
 * <p>进化链由 {@code pet_evolution_config} 配置（阶段 from→to、等级门槛、星光消耗、
 * 属性提升、可选解锁皮肤）；进化后属性一次性写入宠物主表并记录 {@code EVOLVE} 行为留痕。</p>
 */
public interface PetEvolutionService {

    /** 进化状态（当前阶段 + 下一阶条件 + 是否可进化） */
    PetEvolutionVO status(Long userId);

    /** 执行进化（兼容入口：绑定当前主宠；条件不满足抛 409；星光扣减失败整体回滚） */
    PetEvolutionVO evolve(Long userId);

    /**
     * R28 意图冻结进化：必需幂等键（缺键 400）。首次执行冻结 petId/fromStage/toStage/
     * 价格与属性快照，扣款+阶段推进（WHERE evolution_stage=fromStage 条件更新）+解锁同事务；
     * 同键重放返回原进化结果，不重新选择 nextConfig——一次用户意图只推进一个阶段。
     * expectedFromStage 显式校验当前阶段（不匹配 409 PET_STATE_CONFLICT）。
     *
     * @param petId             目标宠物（可空：首次执行绑定当前主宠并冻结）
     * @param expectedFromStage 期望的当前进化阶段（可空：兼容旧客户端）
     */
    PetEvolutionVO evolve(Long userId, Long petId, Integer expectedFromStage);
}
