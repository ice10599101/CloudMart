package com.cloudmart.software.dto.software;

import lombok.Getter;
import lombok.Setter;

/** 发布资格开关请求（方案 4.0）：开通/关闭同一端点，原因必填（审计） */
@Getter
@Setter
public class AdminPermissionToggleRequest {

    /** true=开通 false=关闭 */
    private Boolean enable;

    /** 原因（1~500 字，权限审计要求） */
    private String reason;
}
