package com.cloudmart.software.dto.software;

import lombok.Getter;
import lombok.Setter;

/**
 * 审核决策请求（SW01）：通过/退回定位不可变提交记录 revisionId；
 * reason 必填（1~1000 字，审计要求）；Idempotency-Key 幂等。
 */
@Getter
@Setter
public class SoftwareReviewDecisionRequest {

    private Long softwareId;

    private Boolean approve;

    private String reason;

    /** 请求追踪 ID */
    private String requestId;
}
