package com.cloudmart.community.vo;

import java.time.LocalDateTime;

public record TagVO(
    Long id,
    String name,
    String icon,
    Integer postCount,
    Boolean isHot,
    Integer status,
    LocalDateTime createdAt
) {}
