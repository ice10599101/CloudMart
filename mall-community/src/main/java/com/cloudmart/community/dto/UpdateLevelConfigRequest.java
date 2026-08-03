package com.cloudmart.community.dto;

import jakarta.validation.constraints.Size;

public record UpdateLevelConfigRequest(
    @Size(max = 30) String title,
    Integer minExp,
    String icon,
    String benefits,
    Integer status
) {}
