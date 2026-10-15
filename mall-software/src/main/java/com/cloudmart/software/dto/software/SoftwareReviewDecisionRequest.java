package com.cloudmart.software.dto.software;

import lombok.Getter;
import lombok.Setter;

/**
 * 审核决策请求（方案 7.5）：通过/退回必须绑定提交批次；
 * reason 必填（1~1000 字，审计要求）；Idempotency-Key 幂等。
 */
@Getter
@Setter
public class SoftwareReviewDecisionRequest {

    private String idempotencyKey;

    private Long softwareId;

    /** 提交批次（A07：防旧批次覆盖） */
    private Integer submitSeq;

    /** 软件主体乐观锁版本（防旧审核页面覆盖） */
    private Integer expectedVersion;

    private Boolean approve;

    private String reason;

    /** 请求追踪 ID */
    private String requestId;
}
