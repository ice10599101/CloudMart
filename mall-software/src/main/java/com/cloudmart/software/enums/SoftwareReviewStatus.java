package com.cloudmart.software.enums;

/**
 * 内容快照审核状态（方案 4.2）：DRAFT 可编辑；PENDING 冻结可撤回；
 * APPROVED 已审快照不可原地编辑。
 */
public enum SoftwareReviewStatus {
    DRAFT,
    PENDING,
    APPROVED,
    REJECTED
}
