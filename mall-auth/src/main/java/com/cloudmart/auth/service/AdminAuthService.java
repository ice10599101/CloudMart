package com.cloudmart.auth.service;

import com.cloudmart.auth.dto.LoginRequest;
import com.cloudmart.auth.dto.LoginResponse;
import jakarta.servlet.http.HttpServletRequest;

public interface AdminAuthService {
    LoginResponse login(LoginRequest request, HttpServletRequest httpRequest);
    LoginResponse refresh(String refreshToken);
    /**
     * 退出当前设备（SEC-02）：撤销当前会话及其绑定的管理员刷新家族。
     *
     * @param sid 当前访问令牌的会话标识；为空时保守撤销该主体全部刷新家族
     */
    void logout(Long userId, String sid);

    /** 退出全部设备（SEC-02）：认证状态版本递增 + 撤销全部管理员刷新家族 */
    void logoutAll(Long userId);
}
