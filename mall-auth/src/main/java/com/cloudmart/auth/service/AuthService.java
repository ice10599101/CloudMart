package com.cloudmart.auth.service;

import com.cloudmart.auth.dto.LoginRequest;
import com.cloudmart.auth.dto.LoginResponse;
import com.cloudmart.auth.dto.RefreshRequest;

public interface AuthService {
    LoginResponse login(LoginRequest request);
    LoginResponse refresh(RefreshRequest request);
    /**
     * 登出：撤销当前会话与全部刷新令牌家族。
     *
     * @param sid 当前访问令牌的会话标识（可为空，为空仅撤销刷新令牌）
     */
    void logout(Long userId, String sid);
}
