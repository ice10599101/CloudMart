package com.cloudmart.auth.dto;

import java.util.Set;

public record AdminUserDTO(
    Long id,
    String username,
    String nickname,
    Long deptId,
    Set<String> permissions,
    boolean isSuperAdmin
) {}
