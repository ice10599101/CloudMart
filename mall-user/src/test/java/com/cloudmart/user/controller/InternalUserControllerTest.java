package com.cloudmart.user.controller;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.user.dto.UserDTO;
import com.cloudmart.user.dto.ValidateRequest;
import com.cloudmart.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SEC-04：凭据验证迁移至 /internal/users/validate——控制器行为保持不变，
 * 访问面从匿名公开收敛为服务令牌（ROLE_INTERNAL，由 SecurityConfig 与
 * @PreAuthorize 强制，服务间链路在 mall-auth 侧以出站服务令牌签名）。
 */
class InternalUserControllerTest {

    private MockMvc mockMvc;

    private final UserService userService = Mockito.mock(UserService.class);
    private final com.cloudmart.user.service.AddressService addressService =
            Mockito.mock(com.cloudmart.user.service.AddressService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 1, 1, 0, 0);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InternalUserController(userService, addressService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /internal/users/validate - 验证成功返回信封格式")
    void validateUser_ShouldReturnSuccessEnvelope() throws Exception {
        UserDTO dto = new UserDTO(1L, "xd100001", "test@example.com", "测试用户",
                "avatar.jpg", "签名", "男", "摩羯座", "工程师",
                "北京大学", "北京", "编程", 1, FIXED_TIME, FIXED_TIME);
        given(userService.validateUser(any(ValidateRequest.class))).willReturn(dto);

        mockMvc.perform(post("/internal/users/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ValidateRequest("xd100001", "pass123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.username").value("xd100001"));
    }

    @Test
    @DisplayName("POST /internal/users/validate - 凭据无效返回错误信封")
    void validateUser_WhenInvalidCredentials_ShouldReturnErrorEnvelope() throws Exception {
        willThrow(new BusinessException("AUTH_FAILED", "用户名或密码错误"))
                .given(userService).validateUser(any(ValidateRequest.class));

        mockMvc.perform(post("/internal/users/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ValidateRequest("xd100001", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_FAILED"));
    }
}
