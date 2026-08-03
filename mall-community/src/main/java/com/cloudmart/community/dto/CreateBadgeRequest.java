package com.cloudmart.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateBadgeRequest(
    @NotBlank @Size(max = 50) String name,
    @NotBlank String icon,
    @NotBlank @Size(max = 200) String description,
    String condition,
    Integer level
) {}
