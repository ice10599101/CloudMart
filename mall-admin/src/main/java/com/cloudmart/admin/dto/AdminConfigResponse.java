package com.cloudmart.admin.dto;

import java.time.LocalDateTime;

public record AdminConfigResponse(
    Long id,
    String configName,
    String configKey,
    String configValue,
    Integer configType,
    String remark,
    LocalDateTime createdAt
) {}
