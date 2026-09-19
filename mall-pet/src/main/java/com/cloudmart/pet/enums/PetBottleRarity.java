package com.cloudmart.pet.enums;

/**
 * 捞瓶稀有度（原文档 §19 奖励类型）。
 * NORMAL=普通瓶（真实社区漂流瓶，来自 mall-wish 瓶子池）；
 * RARE/PET/EASTER_EGG=服务端生成的特殊内容瓶（不消耗瓶子池，带额外奖励）。
 */
public enum PetBottleRarity {
    /** 普通瓶：真实社区漂流瓶 */
    NORMAL,
    /** 稀有瓶：特殊内容 + 星光/经验加成 */
    RARE,
    /** 宠物瓶：与宠物系统有关的趣味内容 */
    PET,
    /** 彩蛋瓶：随机彩蛋事件 */
    EASTER_EGG
}
