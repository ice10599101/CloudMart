package com.cloudmart.auth.service;

import com.cloudmart.auth.dto.LoginRequest;
import com.cloudmart.auth.dto.LoginResponse;
import com.cloudmart.auth.dto.RefreshRequest;

public interface AuthService {
    LoginResponse login(LoginRequest request);
    LoginResponse refresh(RefreshRequest request);
    /**
     * 退出当前设备（SEC-02）：撤销当前会话（访问令牌立即失效）及其绑定的
     * 刷新令牌家族；其他设备的会话与家族不受影响。
     *
     * @param sid 当前访问令牌的会话标识；为空时保守撤销该主体全部刷新家族，
     *            绝不做"无操作"式的伪登出
     */
    void logout(Long userId, String sid);

    /**
     * 退出全部设备（SEC-02）：递增主体认证状态版本（全部访问令牌秒级失效）
     * 并撤销全部刷新令牌家族。
     */
    void logoutAll(Long userId);
}
