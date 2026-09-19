package com.cloudmart.pet.enums;

/**
 * 留言墙留言状态：NORMAL（正常）/ HIDDEN（管理员隐藏）/ DELETED（作者或主人删除）。
 *
 * <p>删除与隐藏都保留数据（审核可溯源），用户端只读 NORMAL 状态。</p>
 */
public enum PetWallStatus {
    /** 正常展示 */
    NORMAL,
    /** 管理员隐藏（用户端不可见，管理端可见） */
    HIDDEN,
    /** 已删除（作者或房间主人删除） */
    DELETED
}
