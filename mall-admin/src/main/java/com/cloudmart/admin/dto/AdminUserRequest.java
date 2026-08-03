package com.cloudmart.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AdminUserRequest(
    @NotBlank @Size(min = 3, max = 50) String username,
    String nickname,
    String email,
    String phone,
    Integer sex,
    String avatar,
    @NotBlank @Size(min = 6, max = 100) String password,
    Long deptId,
    List<Long> roleIds,
    List<Long> postIds,
    Integer status,
    String remark
) {}
