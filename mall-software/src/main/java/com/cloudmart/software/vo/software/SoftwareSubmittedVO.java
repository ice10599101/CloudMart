package com.cloudmart.software.vo.software;

import java.time.LocalDateTime;

/** 提交审核结果（方案 7.3）：返回 submitSeq 供后续审核请求绑定批次 */
public record SoftwareSubmittedVO(
        Long softwareId,
        Long revisionId,
        Integer submitSeq,
        Integer lockVersion) {
}
