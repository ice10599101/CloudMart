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

    /** 执行进化（条件不满足抛 409；星光扣减失败整体回滚） */
    PetEvolutionVO evolve(Long userId);
}
