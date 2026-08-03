package com.cloudmart.community.dto;

import jakarta.validation.constraints.Size;

public record UpdateBadgeRequest(
    @Size(max = 50) String name,
    String icon,
    @Size(max = 200) String description,
    String condition,
    Integer level,
    Integer status
) {}
