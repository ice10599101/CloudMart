package com.cloudmart.wish.enums;

/**
 * 相遇信笺状态机（Sprint 3.3，文档 2.10）：
 * PENDING（deliver_after 未到，content 对用户返回 null）→
 * DELIVERED（已投递，可拆信/互动）→ READ（已拆信）。
 */
public enum EncounterLetterStatus {
    PENDING,
    DELIVERED,
    READ
}
