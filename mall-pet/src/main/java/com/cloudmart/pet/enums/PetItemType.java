package com.cloudmart.pet.enums;

/**
 * 宠物背包物品类型（原文档 §89 装备/皮肤/技能 + 三期家园家具）。
 *
 * <p>统一入 {@code pet_inventory}：装备可穿戴到部位、皮肤可穿戴改变外观、
 * 技能书学习后转为 {@code pet_skill} 记录、家具可用于房间主题/摆放。</p>
 */
public enum PetItemType {
    /** 装备（部位穿戴，提供属性加成） */
    EQUIPMENT,
    /** 皮肤（改变外观） */
    SKIN,
    /** 技能书（学习后获得技能） */
    SKILL_BOOK,
    /** 家具（家园装扮：墙纸/地板穿戴，其余摆放） */
    FURNITURE
}
