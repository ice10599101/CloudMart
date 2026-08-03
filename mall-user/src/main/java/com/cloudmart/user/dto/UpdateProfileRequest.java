package com.cloudmart.user.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
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
