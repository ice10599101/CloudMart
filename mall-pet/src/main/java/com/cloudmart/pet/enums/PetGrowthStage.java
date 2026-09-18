package com.cloudmart.pet.enums;

/**
 * 宠物成长阶段：按等级自动推进（Lv1-9 幼年、Lv10-19 成长、Lv20+ 成年），
 * 影响前端形象尺寸与可解锁玩法文案。
 */
public enum PetGrowthStage {
    /** 幼年 Lv1-9 */
    BABY,
    /** 成长期 Lv10-19 */
    YOUNG,
    /** 成年期 Lv20+ */
    ADULT
}
