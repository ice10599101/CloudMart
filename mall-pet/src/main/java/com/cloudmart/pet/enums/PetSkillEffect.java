package com.cloudmart.pet.enums;

/**
 * 宠物技能效果（服务端公式唯一开关，原文档 §12/§13/§89）。
 *
 * <p>效果值语义（{@code pet_skill_config.effect_value}）：</p>
 * <ul>
 *   <li>POWER_STRIKE：首回合伤害提升比例（0-1）</li>
 *   <li>LUCKY_FISH：捞漂流瓶成功率加成（0-1）</li>
 *   <li>QUICK_STEP：战斗先手敏捷加成（点数）</li>
 *   <li>BOOKWORM：读书经验加成比例（0-1）</li>
 *   <li>CHARM_AURA：战斗暴击率加成（0-1）</li>
 *   <li>TOUGH_BODY：受到伤害减免比例（0-1，封顶由引擎限制）</li>
 * </ul>
 */
public enum PetSkillEffect {
    /** 主动技：首回合全力一击 */
    POWER_STRIKE,
    /** 被动：幸运打捞（捞瓶成功率） */
    LUCKY_FISH,
    /** 被动：迅捷身法（先手） */
    QUICK_STEP,
    /** 被动：博览群书（读书经验） */
    BOOKWORM,
    /** 被动：魅力光环（暴击率） */
    CHARM_AURA,
    /** 被动：硬朗体魄（受伤减免） */
    TOUGH_BODY
}
