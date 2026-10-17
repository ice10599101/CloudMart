package com.cloudmart.admin.dto;

import java.util.Set;

public record AdminPermissionsResponse(Long id, String username, String nickname,                                        Set<String> permissions, boolean isSuperAdmin) {}
