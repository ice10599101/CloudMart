package com.cloudmart.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.user.dto.UpdateProfileRequest;
import com.cloudmart.user.service.UserService;
import com.cloudmart.user.vo.UserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminUserControllerTest {

    private MockMvc mockMvc;

    private final UserService userService = Mockito.mock(UserService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 5, 29, 10, 0);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminUserController(userService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private UserVO buildUserVO() {
        return new UserVO(1L, "xd100001", "测试用户", "test@example.com",
                "avatar.jpg", "签名", "男", "2000-01-01", "摩羯座",
                "工程师", "北京大学", "北京", "编程",
                1, FIXED_TIME, FIXED_TIME);
    }

    @Nested
    @DisplayName("GET /admin/users/count")
    class GetMemberCountTests {

        @Test
        @DisplayName("获取会员总数成功返回信封格式")
        void getMemberCount_ShouldReturnSuccessEnvelope() throws Exception {
            given(userService.getMemberCount()).willReturn(100L);

            mockMvc.perform(get("/admin/users/count"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.count").value(100));
        }
    }

    @Nested
    @DisplayName("GET /admin/users")
    class ListUsersTests {

        @Test
        @DisplayName("分页查询用户列表成功返回信封格式")
        void listUsers_ShouldReturnSuccessEnvelope() throws Exception {
            UserVO vo = buildUserVO();
            Page<UserVO> page = new Page<>(1, 20, 1L);
            page.setRecords(List.of(vo));
            given(userService.listUsers(1, 20)).willReturn(page);

            mockMvc.perform(get("/admin/users")
                            .param("page", "1")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].id").value(1))
                    .andExpect(jsonPath("$.meta.page").value(1))
                    .andExpect(jsonPath("$.meta.pageSize").value(20))
                    .andExpect(jsonPath("$.meta.total").value(1));
        }
    }

    @Nested
    @DisplayName("GET /admin/users/{id}")
    class GetUserByIdTests {

        @Test
        @DisplayName("查询用户详情成功返回信封格式")
        void getUserById_ShouldReturnSuccessEnvelope() throws Exception {
            UserVO vo = buildUserVO();
            given(userService.getUserById(1L)).willReturn(vo);

            mockMvc.perform(get("/admin/users/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.nickname").value("测试用户"))
                    .andExpect(jsonPath("$.data.email").value("test@example.com"));
        }

        @Test
        @DisplayName("用户不存在返回错误信封")
        void getUserById_WhenNotFound_ShouldReturnErrorEnvelope() throws Exception {
            willThrow(new BusinessException("USER_NOT_FOUND", "用户不存在"))
                    .given(userService).getUserById(999L);

            mockMvc.perform(get("/admin/users/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("PUT /admin/users/{id}")
    class UpdateUserTests {

        @Test
        @DisplayName("编辑用户信息成功返回信封格式")
        void updateUser_ShouldReturnSuccessEnvelope() throws Exception {
            UserVO updated = new UserVO(1L, "xd100001", "新昵称", "test@example.com",
                    "new-avatar.jpg", "新签名", "男", "2000-01-01", "摩羯座",
                    "工程师", "北京大学", "北京", "编程",
                    1, FIXED_TIME, FIXED_TIME);
            given(userService.updateProfile(eq(1L), any(UpdateProfileRequest.class))).willReturn(updated);

            mockMvc.perform(put("/admin/users/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new UpdateProfileRequest("新昵称", null, "new-avatar.jpg",
                                            "新签名", null, null, null, null, null, null, null))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.nickname").value("新昵称"))
                    .andExpect(jsonPath("$.data.avatar").value("new-avatar.jpg"));
        }
    }

    @Nested
    @DisplayName("PUT /admin/users/{id}/status")
    class ToggleUserStatusTests {

        @Test
        @DisplayName("切换用户状态成功返回信封格式")
        void toggleUserStatus_ShouldReturnSuccessEnvelope() throws Exception {
            willDoNothing().given(userService).toggleUserStatus(1L, 0);

            mockMvc.perform(put("/admin/users/1/status")
                            .param("status", "0"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }
}
