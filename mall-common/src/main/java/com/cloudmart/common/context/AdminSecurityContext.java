package com.cloudmart.common.context;

import java.util.Set;

public record AdminSecurityContext(
    Long userId,
    String username,
    String role,
    Set<String> permissions,
    Long deptId
) {
    private static final ThreadLocal<AdminSecurityContext> CONTEXT = new ThreadLocal<>();

    public static void set(AdminSecurityContext context) {
        CONTEXT.set(context);
    }

    public static AdminSecurityContext get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public boolean isSuperAdmin() {
        return permissions != null && permissions.contains("*:*:*");
    }

    public boolean hasPermission(String permission) {
        if (isSuperAdmin()) {
            return true;
        }
        return permissions != null && permissions.contains(permission);
    }
}
