package com.cloudmart.pet.enums;

/**
 * 关系亲密度增长动作（三期）：决定加多少点，数值在 {@code PetProperties.Relation}。
 *
 * <p>只有"双方都有份"的行为才计入关系亲密度（一起玩、互相留言、彼此对战），
 * 单向行为（如单方面串门）不计，避免刷分。</p>
 */
public enum PetRelationAction {
    /** 好友互访（双方均加） */
    VISIT,
    /** 留言墙互动（留言 / 主人回复，双方均加） */
    WALL,
    /** 两只宠物对战（双方均加） */
    BATTLE
}
