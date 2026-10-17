package com.cloudmart.admin.dto;

import java.util.List;

public record AdminUserUpdateRequest(
    String nickname,
    String email,
    String phone,
    Integer sex,
    String avatar,
    List<Long> roleIds,
    Integer status,
    String remark
) {}
