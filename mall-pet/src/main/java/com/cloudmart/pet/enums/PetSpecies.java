package com.cloudmart.pet.enums;

/**
 * 宠物种类（水果化一期 5 种；扩展只需加枚举值 + 前端形象映射，表结构不变）。
 * 2026-09 猫/柴犬/兔子/狐狸/熊猫 → 草莓/橘子/西瓜/蓝莓/火龙果（V28 迁移，映射与前端 SPECIES_SLOT 一致）。
 */
public enum PetSpecies {
    /** 草莓 */
    STRAWBERRY,
    /** 橘子 */
    ORANGE,
    /** 西瓜 */
    WATERMELON,
    /** 蓝莓 */
    BLUEBERRY,
    /** 火龙果 */
    DRAGONFRUIT
}
