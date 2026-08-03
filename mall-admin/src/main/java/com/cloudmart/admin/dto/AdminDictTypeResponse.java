package com.cloudmart.admin.dto;

import java.time.LocalDateTime;

public record AdminDictTypeResponse(
    Long id,
    String dictName,
    String dictType,
    Integer status,
    String remark,
    LocalDateTime createdAt
) {}
