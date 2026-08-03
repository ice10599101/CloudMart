package com.cloudmart.admin.dto;

import java.time.LocalDateTime;

public record AdminNoticeResponse(
    Long id,
    String noticeTitle,
    Integer noticeType,
    String noticeContent,
    Integer status,
    String remark,
    LocalDateTime createdAt,
    Long readCount,
    Boolean isRead
) {}
