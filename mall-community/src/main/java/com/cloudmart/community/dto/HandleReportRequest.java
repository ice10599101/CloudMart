package com.cloudmart.community.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record HandleReportRequest(
    @NotNull Integer status,
    @Size(max = 500) String handleNote
) {}
