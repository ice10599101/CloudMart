package com.cloudmart.admin.dto;

import java.time.LocalDateTime;

public record AdminDictDataResponse(
    Long id,
    String dictType,
    Integer dictSort,
    String dictLabel,
    String dictValue,
    String cssClass,
    String listClass,
    Integer isDefault,
    Integer status,
    String remark,
    LocalDateTime createdAt
) {}
