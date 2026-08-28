package com.cloudmart.wish.enums;

/**
 * 小组成员状态（Sprint 2.6，文档 1.2 ⑧）。
 *
 * <p>退出/被踢仅标记状态保留历史（互动历史不删除）；
 * 被踢 24h 冷却期内不可加入同关键词小组。</p>
 */
public enum MatchMemberStatus {
    ACTIVE,
    LEFT,
    KICKED
}
