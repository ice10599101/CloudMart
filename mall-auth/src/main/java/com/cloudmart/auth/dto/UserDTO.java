package com.cloudmart.auth.dto;

import java.time.LocalDateTime;

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
