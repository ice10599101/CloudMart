package com.cloudmart.admin.dto;

import java.time.LocalDateTime;

public record AdminOnlineUserResponse(
    Long userId,
    String username,
    String nickname,
    String deptName,
    String ipaddr,
    String loginLocation,
    String browser,
    String os,
    LocalDateTime loginTime,
    String tokenId,
    String userType
) {
    /** Backward-compatible constructor for older admin-only fixtures/callers. */
    public AdminOnlineUserResponse(Long userId, String username, String nickname, String deptName,
                                   String ipaddr, LocalDateTime loginTime, String tokenId) {
        this(userId, username, nickname, deptName, ipaddr, null, null, null,
                loginTime, tokenId, "ADMIN");
    }
}
