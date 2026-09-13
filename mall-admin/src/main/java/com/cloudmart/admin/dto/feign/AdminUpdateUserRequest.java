package com.cloudmart.admin.dto.feign;

import jakarta.validation.constraints.Size;

/**
 * 管理员编辑会员请求，与 mall-user 服务端 UpdateProfileRequest 字段对齐
 * （mall-user V2 迁移已移除 phone 字段）
 */
public record AdminUpdateUserRequest(
    @Size(max = 50) String nickname,
    @Size(max = 100) String email,
    @Size(max = 200) String avatar,
    @Size(max = 200) String signature,
    @Size(max = 10) String gender,
    @Size(max = 20) String birthday,
    @Size(max = 20) String constellation,
    @Size(max = 50) String occupation,
    @Size(max = 100) String school,
    @Size(max = 100) String location,
    @Size(max = 200) String hobbies
) {}
