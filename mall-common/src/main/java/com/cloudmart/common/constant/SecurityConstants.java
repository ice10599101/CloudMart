package com.cloudmart.common.constant;

public final class SecurityConstants {

    private SecurityConstants() {}

    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String INTERNAL_CALL_HEADER = "X-Internal-Call";
    public static final String REFRESH_TOKEN_KEY_PREFIX = "auth:refresh_token:";
    public static final String REFRESH_TOKEN_USER_KEY_PREFIX = "auth:refresh_token_user:";
    public static final String X_REQUEST_ID_HEADER = "X-Request-ID";
    public static final String ADMIN_ROLE_HEADER = "X-Admin-Role";
    public static final String ADMIN_PERMISSIONS_HEADER = "X-Admin-Permissions";
    public static final String ADMIN_DEPT_ID_HEADER = "X-Admin-Dept-Id";
    public static final String ADMIN_USERNAME_HEADER = "X-Admin-Username";
    public static final String ADMIN_LOGIN_FAIL_PREFIX = "admin:login_fail:";
    public static final String ADMIN_LOCK_PREFIX = "admin:lock:";
    public static final String ADMIN_ONLINE_PREFIX = "admin:online:";
}
