package com.cloudmart.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminUserResponse(
    Long id,
    String username,
    String nickname,
    String email,
    String phone,
    Integer sex,
    String avatar,
    Long deptId,
    String deptName,
    Integer status,
    String remark,
    String loginIp,
    LocalDateTime loginDate,
    LocalDateTime pwdUpdateDate,
    LocalDateTime createdAt,
    List<AdminRoleResponse> roles,
    List<AdminPostResponse> posts
) {}
