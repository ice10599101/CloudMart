package com.cloudmart.software.dto.software;

import lombok.Getter;
import lombok.Setter;

/**
 * SF08 补批：作者来源申诉提交。证据为纯文本说明，不接收提取码/密码/受限链接。
 */
@Getter
@Setter
public class SourceAppealCreateRequest {

    private String sourceKey;

    /** 申诉理由（10~1000 字） */
    private String reason;

    /** 证据说明（可空，≤2000 字纯文本） */
    private String evidence;
}
