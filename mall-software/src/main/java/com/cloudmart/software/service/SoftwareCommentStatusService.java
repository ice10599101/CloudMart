package com.cloudmart.software.service;

import com.cloudmart.software.enums.SoftwareCommentStatus;

/**
 * 评论状态迁移与可见计数（SW09）：评论隐藏/删除/审核通过/恢复共用的单一状态动作，
 * 以"带原状态条件的原子迁移"保证从旧状态到新状态只发生一次——
 * 成功迁移后才调整计数、写审计，两个并发处置不会重复扣减。
 */
public interface SoftwareCommentStatusService {

    /**
     * 条件迁移：仅当评论当前状态 = from 时迁移为 to。
     *
     * @param countDelta 迁移成功后对软件可见评论数的增减（0=不变）
     * @return true=本次真正发生迁移（调用方据此发事件等副作用）；false=状态已被并发处置
     */
    boolean transition(Long softwareId, Long commentId, SoftwareCommentStatus from,
                       SoftwareCommentStatus to, int countDelta, Long adminId,
                       String auditAction, String reason);
}
