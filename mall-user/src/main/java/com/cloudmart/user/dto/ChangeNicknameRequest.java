package com.cloudmart.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeNicknameRequest(
    @NotBlank @Size(min = 1, max = 20) String nickname
) {}
