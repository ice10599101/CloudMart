package com.cloudmart.community.dto;

import jakarta.validation.constraints.Size;

public record UpdateTagRequest(
    @Size(max = 50) String name,
    String icon,
    Integer status
) {}
