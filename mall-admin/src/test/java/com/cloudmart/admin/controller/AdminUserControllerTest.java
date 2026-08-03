package com.cloudmart.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.admin.dto.*;
import com.cloudmart.admin.service.AdminUserService;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminUserControllerTest {

    private MockMvc mockMvc;
    private AdminUserService adminUserService;

    @BeforeEach
    void setUp() {
        adminUserService = mock(AdminUserService.class);
        AdminUserController controller = new AdminUserController(adminUserService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void page_returnsPagedUsers() throws Exception {
        Page<AdminUserResponse> page = new Page<>(1, 10, 1);
        AdminUserResponse userResponse = new AdminUserResponse(
                1L, "admin", "Admin", "admin@test.com", "13800000001",
                0, null, 1L, "技术部", 0, null, "127.0.0.1",
                LocalDateTime.now(), null, LocalDateTime.now(),
                List.of(), List.of()
        );
        page.setRecords(List.of(userResponse));
        given(adminUserService.page(any(AdminUserQueryRequest.class))).willReturn(page);

        mockMvc.perform(get("/users/page").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.records[0].username").value("admin"))
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    void getById_returnsUserDetail() throws Exception {
        AdminUserResponse userResponse = new AdminUserResponse(
                1L, "admin", "Admin", "admin@test.com", "13800000001",
                0, null, 1L, "技术部", 0, null, null,
                null, null, null, List.of(), List.of()
        );
        given(adminUserService.getById(1L)).willReturn(userResponse);

        mockMvc.perform(get("/users/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.username").value("admin"));
    }

    @Test
    void create_userCreatedSuccessfully() throws Exception {
        doNothing().when(adminUserService).create(any(AdminUserRequest.class));

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"newuser\",\"nickname\":\"New\",\"email\":\"new@test.com\"," +
                                "\"phone\":\"13800000000\",\"status\":0,\"deptId\":1,\"password\":\"pass123\"," +
                                "\"sex\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void update_userUpdatedSuccessfully() throws Exception {
        doNothing().when(adminUserService).update(anyLong(), any(AdminUserUpdateRequest.class));

        mockMvc.perform(put("/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"Updated\",\"email\":\"updated@test.com\"," +
                                "\"phone\":\"13800000002\",\"status\":0,\"deptId\":1,\"sex\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void delete_userDeletedSuccessfully() throws Exception {
        doNothing().when(adminUserService).delete(1L);

        mockMvc.perform(delete("/users/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void updateStatus_statusChangedSuccessfully() throws Exception {
        doNothing().when(adminUserService).updateStatus(1L, 1);

        mockMvc.perform(put("/users/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void resetPassword_passwordResetSuccessfully() throws Exception {
        doNothing().when(adminUserService).resetPassword(any(AdminResetPwdRequest.class));

        mockMvc.perform(put("/users/resetPassword")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"newPassword\":\"newPass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void assignRoles_rolesAssignedSuccessfully() throws Exception {
        doNothing().when(adminUserService).assignRoles(anyLong(), anyList());

        mockMvc.perform(put("/users/1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\":[1,2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
