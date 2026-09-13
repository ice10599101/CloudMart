package com.cloudmart.admin.dto.feign;

import java.time.LocalDateTime;

/**
 * 用户 Feign 传输对象，与 mall-user 服务端 UserDTO 字段对齐
 */
public record UserDTO(
    Long id,
    String username,
    String email,
    String nickname,
    String avatar,
    String gender,
    String signature,
    String birthday,
    String occupation,
    String school,
    String location,
    String hobbies,
    Integer status,
    LocalDateTime createdAt
) {}
