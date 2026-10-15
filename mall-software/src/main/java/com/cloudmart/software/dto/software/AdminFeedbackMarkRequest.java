package com.cloudmart.software.dto.software;

import lombok.Getter;
import lombok.Setter;

/** 失效反馈标记（方案 5.3）：RESOLVED 必须携带修复 revisionId */
@Getter
@Setter
public class AdminFeedbackMarkRequest {

    private String idempotencyKey;

    /** VERIFYING/RESOLVED/REJECTED */
    private String status;

    /** 修复上线快照 ID（RESOLVED 必填；保留 original 指纹） */
    private Long resolvedRevisionId;

    private String note;
}
