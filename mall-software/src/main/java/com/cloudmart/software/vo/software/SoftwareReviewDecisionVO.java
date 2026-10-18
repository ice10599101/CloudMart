package com.cloudmart.software.vo.software;

/** 审核决策结果（SW01）：定位本次不可变提交记录，不返回批次/版本号 */
public record SoftwareReviewDecisionVO(
        Long softwareId,
        Long revisionId,
        String decision) {
}
