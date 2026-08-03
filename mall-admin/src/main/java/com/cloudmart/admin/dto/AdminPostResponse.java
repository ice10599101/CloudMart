package com.cloudmart.admin.dto;

import java.time.LocalDateTime;

public record AdminPostResponse(
    Long id,
    String postCode,
    String postName,
    Integer orderNum,
    Integer status,
    String remark,
    LocalDateTime createdAt
) {}
