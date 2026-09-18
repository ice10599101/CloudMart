package com.cloudmart.pet.enums;

/**
 * 对战状态：PVP 挑战先落 PENDING（含双方快照与随机种子），防守方 accept 后
 * 才用快照计算并 FINISHED；拒绝 DECLINED；超时未应战 EXPIRED。PvE 直接 FINISHED。
 */
public enum PetBattleStatus {
    PENDING,
    FINISHED,
    DECLINED,
    EXPIRED
}
