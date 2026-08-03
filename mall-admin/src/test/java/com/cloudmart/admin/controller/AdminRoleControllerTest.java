package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.AdminRoleResponse;
import com.cloudmart.admin.dto.AdminRoleRequest;
import com.cloudmart.admin.service.AdminRoleService;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminRoleControllerTest {

    private MockMvc mockMvc;
    private AdminRoleService adminRoleService;

    @BeforeEach
    void setUp() {
        adminRoleService = mock(AdminRoleService.class);
        AdminRoleController controller = new AdminRoleController(adminRoleService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void list_returnsAllRoles() throws Exception {
        AdminRoleResponse role = new AdminRoleResponse(1L, "管理员", "admin", 1, 1, 1, 1, 0, null, LocalDateTime.now());
        given(adminRoleService.list()).willReturn(List.of(role));

        mockMvc.perform(get("/roles").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].roleKey").value("admin"));
    }

    @Test
    void getById_returnsRoleDetail() throws Exception {
        AdminRoleResponse role = new AdminRoleResponse(1L, "管理员", "admin", 1, 1, 1, 1, 0, null, LocalDateTime.now());
        given(adminRoleService.getById(1L)).willReturn(role);

        mockMvc.perform(get("/roles/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void create_roleCreatedSuccessfully() throws Exception {
        doNothing().when(adminRoleService).create(any(AdminRoleRequest.class));

        mockMvc.perform(post("/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleKey\":\"editor\",\"roleName\":\"编辑\",\"roleSort\":2,\"dataScope\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void update_roleUpdatedSuccessfully() throws Exception {
        doNothing().when(adminRoleService).update(anyLong(), any(AdminRoleRequest.class));

        mockMvc.perform(put("/roles/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleKey\":\"admin\",\"roleName\":\"超级管理员\",\"roleSort\":1,\"dataScope\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void delete_roleDeletedSuccessfully() throws Exception {
        doNothing().when(adminRoleService).delete(1L);

        mockMvc.perform(delete("/roles/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void assignMenus_menusAssignedSuccessfully() throws Exception {
        doNothing().when(adminRoleService).assignMenus(any());

        mockMvc.perform(put("/roles/menus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":1,\"menuIds\":[1,2,3]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void assignDepts_deptsAssignedSuccessfully() throws Exception {
        doNothing().when(adminRoleService).assignDepts(any());

        mockMvc.perform(put("/roles/depts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":1,\"deptIds\":[10,20]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getRoleMenuIds_returnsMenuIdList() throws Exception {
        given(adminRoleService.getMenuIdsByRoleId(1L)).willReturn(List.of(1L, 2L, 3L));

        mockMvc.perform(get("/roles/1/menus").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    @Test
    void updateDataScope_dataScopeUpdatedSuccessfully() throws Exception {
        doNothing().when(adminRoleService).updateDataScope(anyLong(), any());

        mockMvc.perform(put("/roles/1/data-scope")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataScope\":3,\"deptIds\":[10,20]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
