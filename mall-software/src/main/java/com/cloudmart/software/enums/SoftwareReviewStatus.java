package com.cloudmart.software.enums;

/**
 * 内容快照审核状态（方案 4.2 / SW04）：DRAFT 可编辑；CHECKING 提交后内容已冻结、
 * 自动检查进行中（结果只能是该 revision，超时不伪装成通过）；PENDING 检查通过等待人工；
 * WITHDRAWN 作者撤回的旧提交（内容保持不变，不可再编辑/审核）；
 * APPROVED 已审快照不可原地编辑；REJECTED 退回后保留原记录并生成新 DRAFT。
 */
public enum SoftwareReviewStatus {
    DRAFT,
    CHECKING,
    PENDING,
    WITHDRAWN,
    APPROVED,
    REJECTED
}
