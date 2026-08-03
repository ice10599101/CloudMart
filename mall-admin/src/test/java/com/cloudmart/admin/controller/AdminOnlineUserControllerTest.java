package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.AdminOnlineUserResponse;
import com.cloudmart.admin.service.AdminOnlineUserService;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminOnlineUserControllerTest {

    private MockMvc mockMvc;
    private AdminOnlineUserService adminOnlineUserService;

    @BeforeEach
    void setUp() {
        adminOnlineUserService = mock(AdminOnlineUserService.class);
        AdminOnlineUserController controller = new AdminOnlineUserController(adminOnlineUserService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("GET /online/list - 在线用户列表")
    class ListTests {

        @Test
        @DisplayName("返回在线用户列表")
        void list_returnsOnlineUsers() throws Exception {
            AdminOnlineUserResponse response = new AdminOnlineUserResponse(
                    1L, "admin", "Admin", "技术部", "127.0.0.1", LocalDateTime.now(), "token-abc");
            given(adminOnlineUserService.list()).willReturn(List.of(response));

            mockMvc.perform(get("/online/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[0].username").value("admin"))
                    .andExpect(jsonPath("$.data[0].tokenId").value("token-abc"));

            verify(adminOnlineUserService).list();
        }
    }

    @Nested
    @DisplayName("DELETE /online/{tokenId} - 强制下线")
    class ForceLogoutTests {

        @Test
        @DisplayName("强制下线用户成功")
        void forceLogout_userForceLoggedOutSuccessfully() throws Exception {
            doNothing().when(adminOnlineUserService).forceLogout("token-abc");

            mockMvc.perform(delete("/online/token-abc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(adminOnlineUserService).forceLogout("token-abc");
        }
    }
}
