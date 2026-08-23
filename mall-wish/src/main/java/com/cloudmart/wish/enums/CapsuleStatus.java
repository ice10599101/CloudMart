package com.cloudmart.wish.enums;

/**
 * 时间胶囊状态机（文档 2.7 / 表⑦）。
 *
 * <pre>
 * SEALED --(mall-job 扫描 open_at 到期, CAS)--> AVAILABLE
 * SEALED/AVAILABLE --(用户点击开启, CAS)--> OPENED
 * SEALED/AVAILABLE --(用户取消)--> CANCELLED
 * </pre>
 *
 * <p>到期判定唯一依据 UTC open_at（文档 26.3：用户跨时区旅行不影响到期）；
 * 开启接口对 SEALED 且已到期同样放行（容忍扫描间隙/时钟偏移，创建即到期
 * 立即可开启的边界语义）。</p>
 */
public enum CapsuleStatus {
    /** 封印中（未到期，内容不可见） */
    SEALED,
    /** 已到期待开启（扫描任务流转，内容开启前仍不可见） */
    AVAILABLE,
    /** 已开启（内容可见） */
    OPENED,
    /** 已取消 */
    CANCELLED
}
