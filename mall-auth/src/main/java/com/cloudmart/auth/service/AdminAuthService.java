package com.cloudmart.auth.service;

import com.cloudmart.auth.dto.LoginRequest;
import com.cloudmart.auth.dto.LoginResponse;
import jakarta.servlet.http.HttpServletRequest;

public interface AdminAuthService {
    LoginResponse login(LoginRequest request, HttpServletRequest httpRequest);
    LoginResponse refresh(String refreshToken);
    void logout(Long userId);
}
