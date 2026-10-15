package com.cloudmart.software.dto.software;

import lombok.Getter;
import lombok.Setter;

/** 评论人工处置（方案 6.8）：APPROVE/HIDE/RESTORE/REJECT */
@Getter
@Setter
public class AdminCommentModerateRequest {

    /** APPROVE（PENDING→VISIBLE）/HIDE（VISIBLE→HIDDEN）/RESTORE（HIDDEN→VISIBLE）/REJECT（PENDING→HIDDEN） */
    private String action;

    private String reason;
}
