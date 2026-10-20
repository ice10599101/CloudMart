package com.cloudmart.software.vo.software;

/**
 * 提交审核受理回执（SW04）：返回不可变提交记录与审核状态，不含提交批次/版本号。
 * operationId 为提交幂等键，客户端可用于重试与查询检查进展。
 */
public record SoftwareSubmittedVO(
        Long softwareId,
        Long revisionId,
        String reviewStatus,
        String operationId) {
}
