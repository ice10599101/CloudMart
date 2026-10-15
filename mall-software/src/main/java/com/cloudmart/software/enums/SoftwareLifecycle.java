package com.cloudmart.software.enums;

/**
 * 软件生命周期（方案 4.2）：作者下架为 UNLISTED；平台处置为 BANNED；
 * 不可用草稿重提绕过 BANNED。
 */
public enum SoftwareLifecycle {
    /** 在架（公开可见还需已审快照指针） */
    ACTIVE,
    /** 作者下架/平台解除封禁后落点 */
    UNLISTED,
    /** 平台封禁（解除回 UNLISTED，由作者恢复展示） */
    BANNED,
    /** 软删除 */
    DELETED
}
