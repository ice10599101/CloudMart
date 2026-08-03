package com.cloudmart.community.vo;

import java.time.LocalDateTime;

public record ExpLogVO(
    Long id,
    Integer expChange,
    String source,
    Long bizId,
    String description,
    LocalDateTime createdAt
) {}
