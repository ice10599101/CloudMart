package com.cloudmart.user.dto;

import java.time.LocalDateTime;

public record UserDTO(
    Long id,
    String username,
    String email,
    String nickname,
    String avatar,
    String signature,
    String gender,
    String constellation,
    String occupation,
    String school,
    String location,
    String hobbies,
    Integer status,
    LocalDateTime nicknameUpdatedAt,
    LocalDateTime createdAt
) {}
