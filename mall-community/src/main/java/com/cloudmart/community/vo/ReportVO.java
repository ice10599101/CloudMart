package com.cloudmart.community.vo;

import java.time.LocalDateTime;
import java.util.List;

public record ReportVO(
    Long id,
    Long reporterId,
    String reporterNickname,
    String targetType,
    Long targetId,
    String reason,
    String description,
    List<String> images,
    Integer status,
    Long handlerId,
    String handlerNickname,
    String handleNote,
    LocalDateTime handledAt,
    LocalDateTime createdAt
) {}
