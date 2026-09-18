package com.cloudmart.pet.enums;

/**
 * 宠物记忆类型（结构化记忆，控制上下文成本；原文档 §26）。
 */
public enum PetMemoryType {
    /** 主人的喜好（如"主人喜欢猫"） */
    FAVORITE,
    /** 主人的习惯（如"喜欢晚上聊天"） */
    HABIT,
    /** 关于主人的事实（如"主人叫小冰"） */
    FACT
}
