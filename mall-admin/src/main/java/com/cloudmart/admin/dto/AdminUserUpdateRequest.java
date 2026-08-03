package com.cloudmart.admin.dto;

import java.util.List;

public record AdminUserUpdateRequest(
    String nickname,
    String email,
    String phone,
    Integer sex,
    String avatar,
    Long deptId,
    List<Long> roleIds,
    List<Long> postIds,
    Integer status,
    String remark
) {}
