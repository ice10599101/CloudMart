package com.cloudmart.admin.dto.feign;

import java.time.LocalDateTime;

/**
 * 用户 Feign 传输对象，与 mall-user 服务端 UserDTO 字段对齐
 */
public record UserDTO(
    Long id,
    String username,
    String email,
    String phone,
    String nickname,
    String avatar,
    Integer status,
    LocalDateTime createdAt
) {}
