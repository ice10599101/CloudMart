package com.cloudmart.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateReportRequest(
    @NotBlank String targetType,
    @NotNull Long targetId,
    @NotBlank @Size(max = 50) String reason,
    @Size(max = 500) String description,
    List<String> images
) {}
