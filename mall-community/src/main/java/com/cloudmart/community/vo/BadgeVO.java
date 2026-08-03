package com.cloudmart.community.vo;

import java.time.LocalDateTime;

public record BadgeVO(
    Long id,
    String name,
    String icon,
    String description,
    String condition,
    Integer level,
    Integer status,
    LocalDateTime createdAt
) {}
