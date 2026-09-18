package com.cloudmart.pet.enums;

/**
 * 对战模式：一期 PvE（野生宠物，立即结算）+ 异步 PvP（快照 + 防守方 accept 后结算）；
 * 实时 PvP 留三期（原文档 §79 分阶段建议）。
 */
public enum PetBattleMode {
    /** PvE：挑战系统生成的野生宠物 */
    PVE,
    /** 异步 PvP：挑战其他用户宠物，防守方 accept 后服务端按快照计算 */
    PVP
}
