package com.cloudmart.software.vo.software;

import java.time.LocalDateTime;

/** 审核决策结果（方案 7.5）：绑定批次与软件版本 */
public record SoftwareReviewDecisionVO(
        Long softwareId,
        Long revisionId,
        String decision,
        Integer submitSeq,
        Integer publicationSeq,
        Integer lockVersion) {
}
