package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.AdminPermissionsResponse;
import com.cloudmart.admin.dto.AdminValidateRequest;
import com.cloudmart.admin.dto.AdminValidateResponse;
import com.cloudmart.admin.entity.AdminUser;
import com.cloudmart.admin.service.AdminAuthService;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminAuthControllerTest {

    private MockMvc mockMvc;
    private AdminAuthService adminAuthService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        adminAuthService = mock(AdminAuthService.class);
        AdminAuthController controller = new AdminAuthController(adminAuthService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("POST /auth/validate - 验证管理员凭据")
    class ValidateAdminTests {

        @Test
        @DisplayName("内部调用验证成功返回用户信息和权限")
        void validate_withInternalHeader_returnsValidateResponse() throws Exception {
            AdminUser adminUser = new AdminUser();
            adminUser.setId(1L);
            adminUser.setUsername("admin");
            adminUser.setNickname("Admin");
            adminUser.setDeptId(1L);
            given(adminAuthService.validateCredentials("admin", "pass123")).willReturn(adminUser);
            given(adminAuthService.resolvePermissions(1L)).willReturn(Set.of("*:*:*"));
            given(adminAuthService.checkSuperAdmin(1L)).willReturn(true);

            AdminValidateRequest request = new AdminValidateRequest("admin", "pass123");

            mockMvc.perform(post("/auth/validate")
                            .header(SecurityConstants.INTERNAL_CALL_HEADER, "true")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.username").value("admin"))
                    .andExpect(jsonPath("$.data.isSuperAdmin").value(true));

            verify(adminAuthService).validateCredentials("admin", "pass123");
            verify(adminAuthService).resolvePermissions(1L);
        }

        @Test
        @DisplayName("缺少内部调用头返回403")
        void validate_withoutInternalHeader_returnsForbidden() throws Exception {
            AdminValidateRequest request = new AdminValidateRequest("admin", "pass123");

            mockMvc.perform(post("/auth/validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("用户名密码为空返回业务异常")
        void validate_withNullFields_returnsValidationError() throws Exception {
            AdminValidateRequest request = new AdminValidateRequest(null, null);

            mockMvc.perform(post("/auth/validate")
                            .header(SecurityConstants.INTERNAL_CALL_HEADER, "true")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
    }

    @Nested
    @DisplayName("GET /auth/permissions/{userId} - 获取用户权限")
    class GetPermissionsTests {

        @Test
        @DisplayName("内部调用获取用户权限成功")
        void getPermissions_withInternalHeader_returnsPermissions() throws Exception {
            AdminUser adminUser = new AdminUser();
            adminUser.setId(1L);
            adminUser.setUsername("admin");
            adminUser.setNickname("Admin");
            adminUser.setDeptId(1L);
            given(adminAuthService.getUserById(1L)).willReturn(adminUser);
            given(adminAuthService.resolvePermissions(1L)).willReturn(Set.of("admin:user:list", "admin:role:list"));
            given(adminAuthService.checkSuperAdmin(1L)).willReturn(false);

            mockMvc.perform(get("/auth/permissions/1")
                            .header(SecurityConstants.INTERNAL_CALL_HEADER, "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.username").value("admin"))
                    .andExpect(jsonPath("$.data.isSuperAdmin").value(false));

            verify(adminAuthService).getUserById(1L);
            verify(adminAuthService).resolvePermissions(1L);
        }

        @Test
        @DisplayName("缺少内部调用头返回403")
        void getPermissions_withoutInternalHeader_returnsForbidden() throws Exception {
            mockMvc.perform(get("/auth/permissions/1"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }
    }
}
