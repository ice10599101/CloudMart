package com.cloudmart.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.user.dto.ChangeNicknameRequest;
import com.cloudmart.user.dto.ChangePasswordRequest;
import com.cloudmart.user.dto.RegisterRequest;
import com.cloudmart.user.dto.UpdateProfileRequest;
import com.cloudmart.user.dto.UserDTO;
import com.cloudmart.user.dto.ValidateRequest;
import com.cloudmart.user.service.UserService;
import com.cloudmart.user.vo.UserVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest {

    private MockMvc mockMvc;

    private final UserService userService = Mockito.mock(UserService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 5, 29, 10, 0);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private void setSecurityContext(Long userId) {
        Authentication auth = new TestingAuthenticationToken(String.valueOf(userId), null);
        SecurityContext context = Mockito.mock(SecurityContext.class);
        given(context.getAuthentication()).willReturn(auth);
        SecurityContextHolder.setContext(context);
    }

    private void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private UserVO buildUserVO() {
        return new UserVO(1L, "xd100001", "测试用户", "test@example.com",
                "avatar.jpg", "签名", "男", "2000-01-01", "摩羯座",
                "工程师", "北京大学", "北京", "编程",
                1, FIXED_TIME, FIXED_TIME);
    }

    @Test
    @DisplayName("POST /users/register - 注册成功返回信封格式")
    void register_ShouldReturnSuccessEnvelope() throws Exception {
        UserVO vo = buildUserVO();
        given(userService.register(any(RegisterRequest.class))).willReturn(vo);

        mockMvc.perform(post("/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("pass123456", "test@example.com", "测试用户"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.nickname").value("测试用户"))
                .andExpect(jsonPath("$.data.email").value("test@example.com"));
    }

    @Test
    @DisplayName("POST /users/register - 邮箱重复返回错误信封")
    void register_WhenEmailDuplicate_ShouldReturnErrorEnvelope() throws Exception {
        willThrow(new BusinessException("VALIDATION_ERROR", "邮箱已被注册"))
                .given(userService).register(any(RegisterRequest.class));

        mockMvc.perform(post("/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("pass123456", "dup@example.com", "用户"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /users/validate - 验证用户凭据成功返回信封格式")
    void validateUser_ShouldReturnSuccessEnvelope() throws Exception {
        UserDTO dto = new UserDTO(1L, "xd100001", "test@example.com", "测试用户",
                "avatar.jpg", "签名", "男", "摩羯座", "工程师",
                "北京大学", "北京", "编程", 1, FIXED_TIME, FIXED_TIME);
        given(userService.validateUser(any(ValidateRequest.class))).willReturn(dto);

        mockMvc.perform(post("/users/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ValidateRequest("xd100001", "pass123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.username").value("xd100001"));
    }

    @Test
    @DisplayName("POST /users/validate - 凭据无效返回错误信封")
    void validateUser_WhenInvalidCredentials_ShouldReturnErrorEnvelope() throws Exception {
        willThrow(new BusinessException("AUTH_FAILED", "用户名或密码错误"))
                .given(userService).validateUser(any(ValidateRequest.class));

        mockMvc.perform(post("/users/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ValidateRequest("xd100001", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_FAILED"));
    }

    @Test
    @DisplayName("GET /users/me - 获取当前用户信息返回信封格式")
    void getCurrentUser_ShouldReturnSuccessEnvelope() throws Exception {
        setSecurityContext(1L);
        UserVO vo = buildUserVO();
        given(userService.getUserById(1L)).willReturn(vo);

        try {
            mockMvc.perform(get("/users/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.nickname").value("测试用户"));
        } finally {
            clearSecurityContext();
        }
    }

    @Test
    @DisplayName("GET /users/me - 未登录返回错误信封")
    void getCurrentUser_WhenNotAuthenticated_ShouldReturnErrorEnvelope() throws Exception {
        clearSecurityContext();

        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /users/{id} - 获取用户信息返回信封格式")
    void getUserById_ShouldReturnSuccessEnvelope() throws Exception {
        UserVO vo = buildUserVO();
        given(userService.getUserById(1L)).willReturn(vo);

        mockMvc.perform(get("/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("GET /users/{id} - 用户不存在返回错误信封")
    void getUserById_WhenNotFound_ShouldReturnErrorEnvelope() throws Exception {
        willThrow(new BusinessException("USER_NOT_FOUND", "用户不存在"))
                .given(userService).getUserById(999L);

        mockMvc.perform(get("/users/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /users/search - 搜索用户返回信封格式")
    void searchUsers_ShouldReturnSuccessEnvelope() throws Exception {
        UserVO vo = buildUserVO();
        given(userService.searchUsers("测试", 1, 10)).willReturn(List.of(vo));

        mockMvc.perform(get("/users/search")
                        .param("keyword", "测试")
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1));
    }

    @Test
    @DisplayName("GET /users/batch - 批量获取用户信息返回信封格式")
    void batchGetUsers_ShouldReturnSuccessEnvelope() throws Exception {
        UserVO vo = buildUserVO();
        given(userService.batchGetUsers(List.of(1L, 2L))).willReturn(List.of(vo));

        mockMvc.perform(get("/users/batch")
                        .param("ids", "1", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1));
    }

    @Test
    @DisplayName("PUT /users/profile - 更新用户资料返回信封格式")
    void updateProfile_ShouldReturnSuccessEnvelope() throws Exception {
        setSecurityContext(1L);
        UserVO updated = new UserVO(1L, "xd100001", "新昵称", "test@example.com",
                "new-avatar.jpg", "新签名", "男", "2000-01-01", "摩羯座",
                "工程师", "北京大学", "北京", "编程",
                1, FIXED_TIME, FIXED_TIME);
        given(userService.updateProfile(eq(1L), any(UpdateProfileRequest.class))).willReturn(updated);

        try {
            mockMvc.perform(put("/users/profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new UpdateProfileRequest("新昵称", null, "new-avatar.jpg",
                                            "新签名", null, null, null, null, null, null, null))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.nickname").value("新昵称"))
                    .andExpect(jsonPath("$.data.avatar").value("new-avatar.jpg"));
        } finally {
            clearSecurityContext();
        }
    }

    @Test
    @DisplayName("PUT /users/nickname - 修改昵称返回信封格式")
    void changeNickname_ShouldReturnSuccessEnvelope() throws Exception {
        setSecurityContext(1L);
        UserVO updated = new UserVO(1L, "xd100001", "新昵称", "test@example.com",
                "avatar.jpg", "签名", "男", "2000-01-01", "摩羯座",
                "工程师", "北京大学", "北京", "编程",
                1, FIXED_TIME, FIXED_TIME);
        given(userService.changeNickname(eq(1L), any(ChangeNicknameRequest.class))).willReturn(updated);

        try {
            mockMvc.perform(put("/users/nickname")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new ChangeNicknameRequest("新昵称"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.nickname").value("新昵称"));
        } finally {
            clearSecurityContext();
        }
    }

    @Test
    @DisplayName("PUT /users/nickname - 昵称重复返回错误信封")
    void changeNickname_WhenDuplicate_ShouldReturnErrorEnvelope() throws Exception {
        setSecurityContext(1L);
        willThrow(new BusinessException("VALIDATION_ERROR", "昵称已被使用"))
                .given(userService).changeNickname(eq(1L), any(ChangeNicknameRequest.class));

        try {
            mockMvc.perform(put("/users/nickname")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new ChangeNicknameRequest("重复昵称"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        } finally {
            clearSecurityContext();
        }
    }

    @Test
    @DisplayName("PUT /users/password - 修改密码成功返回信封格式")
    void changePassword_ShouldReturnSuccessEnvelope() throws Exception {
        setSecurityContext(1L);
        willDoNothing().given(userService).changePassword(eq(1L), any(ChangePasswordRequest.class));

        try {
            mockMvc.perform(put("/users/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new ChangePasswordRequest("oldPass123", "newPass456"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        } finally {
            clearSecurityContext();
        }
    }

    @Test
    @DisplayName("PUT /users/password - 旧密码错误返回错误信封")
    void changePassword_WhenOldPasswordWrong_ShouldReturnErrorEnvelope() throws Exception {
        setSecurityContext(1L);
        willThrow(new BusinessException("AUTH_FAILED", "旧密码错误"))
                .given(userService).changePassword(eq(1L), any(ChangePasswordRequest.class));

        try {
            mockMvc.perform(put("/users/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new ChangePasswordRequest("wrongOld", "newPass456"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("AUTH_FAILED"));
        } finally {
            clearSecurityContext();
        }
    }

    @Test
    @DisplayName("GET /users/recommend - 推荐用户列表返回信封格式")
    void recommendUsers_ShouldReturnSuccessEnvelope() throws Exception {
        UserVO vo = buildUserVO();
        Page<UserVO> page = new Page<>(1, 6, 1L);
        page.setRecords(List.of(vo));
        given(userService.listUsers(1, 6)).willReturn(page);

        mockMvc.perform(get("/users/recommend")
                        .param("limit", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1));
    }

    @Test
    @DisplayName("GET /users/page - 分页查询用户返回信封格式")
    void listUsers_ShouldReturnSuccessEnvelope() throws Exception {
        UserVO vo = buildUserVO();
        Page<UserVO> page = new Page<>(1, 20, 1L);
        page.setRecords(List.of(vo));
        given(userService.listUsers(1, 20)).willReturn(page);

        mockMvc.perform(get("/users/page")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.records[0].id").value(1));
    }

    @Test
    @DisplayName("PUT /users/{id}/status - 切换用户状态返回信封格式")
    void toggleUserStatus_ShouldReturnSuccessEnvelope() throws Exception {
        willDoNothing().given(userService).toggleUserStatus(1L, 0);

        mockMvc.perform(put("/users/1/status")
                        .param("status", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /users/count - 获取用户总数返回信封格式")
    void getMemberCount_ShouldReturnSuccessEnvelope() throws Exception {
        given(userService.getMemberCount()).willReturn(100L);

        mockMvc.perform(get("/users/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(100));
    }
}
