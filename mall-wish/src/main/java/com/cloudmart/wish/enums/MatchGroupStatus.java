package com.cloudmart.wish.enums;

/**
 * 同愿小组状态机（Sprint 2.6，文档 2.8）。
 *
 * <p>流转：OPEN → FULL（加满）；FULL → OPEN（有人退出）；
 * 任意态 → CLOSED（组长解散 / 组长退出且无可转让成员）。</p>
 */
public enum MatchGroupStatus {
    /** 可加入 */
    OPEN,
    /** 已满员 */
    FULL,
    /** 已关闭（解散或无人） */
    CLOSED
}
