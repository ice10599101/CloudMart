package com.cloudmart.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminDeptResponse(
    Long id,
    Long parentId,
    String ancestors,
    String deptName,
    Integer orderNum,
    String leader,
    String phone,
    String email,
    Integer status,
    LocalDateTime createdAt,
    List<AdminDeptResponse> children
) {}
