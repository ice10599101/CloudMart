package com.cloudmart.admin.dto;

import java.time.LocalDateTime;

public record AdminOnlineUserResponse(
    Long userId,
    String username,
    String nickname,
    String deptName,
    String loginIp,
    LocalDateTime loginTime,
    String tokenId
) {}
