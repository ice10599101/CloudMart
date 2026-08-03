package com.cloudmart.admin.dto;

import java.time.LocalDateTime;
import java.util.Set;

public record AdminProfileResponse(
    Long id,
    String username,
    String nickname,
    String email,
    String phone,
    String avatar,
    Long deptId,
    Integer status,
    LocalDateTime createdAt,
    Set<String> permissions,
    Set<String> roles
) {}
