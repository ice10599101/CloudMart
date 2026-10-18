package com.cloudmart.software.vo.software;

/** 提交审核结果（SW01）：返回不可变提交记录与审核状态，不含提交批次/版本号 */
public record SoftwareSubmittedVO(
        Long softwareId,
        Long revisionId,
        String reviewStatus) {
}
