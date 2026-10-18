package com.cloudmart.software.enums;

/**
 * 内容快照审核状态（方案 4.2 / SW04）：DRAFT 可编辑；PENDING 冻结待审；
 * WITHDRAWN 作者撤回的旧提交（内容保持不变，不可再编辑/审核）；
 * APPROVED 已审快照不可原地编辑；REJECTED 退回后可编辑。
 */
public enum SoftwareReviewStatus {
    DRAFT,
    PENDING,
    WITHDRAWN,
    APPROVED,
    REJECTED
}
