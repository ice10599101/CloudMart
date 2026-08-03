package com.cloudmart.admin.dto.feign;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 管理员编辑会员请求，与 mall-user 服务端 UpdateProfileRequest 字段对齐
 */
public record AdminUpdateUserRequest(
    @Size(max = 50) String nickname,
    @Size(max = 20) String phone,
    @Size(max = 100) String email
) {}
