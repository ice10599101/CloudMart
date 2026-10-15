package com.cloudmart.software.dto.software;

import lombok.Getter;
import lombok.Setter;

/** 举报处置（方案 6.12）：仅标记成立不隐式封禁，处置动作显式选择 */
@Getter
@Setter
public class AdminReportHandleRequest {

    /** RESOLVED（成立）/REJECTED（驳回） */
    private String status;

    /** NONE/BAN_SOFTWARE/HIDE_COMMENT（仅 RESOLVED 时有效） */
    private String action;

    private String note;
}
