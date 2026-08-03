package com.cloudmart.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminNoticeRequest(
    @NotBlank String noticeTitle,
    Integer noticeType,
    String noticeContent,
    Integer status,
    String remark
) {}
