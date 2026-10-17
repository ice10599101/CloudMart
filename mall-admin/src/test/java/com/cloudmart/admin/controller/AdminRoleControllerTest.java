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
    void getRoleMenuIds_returnsMenuIdList() throws Exception {
        given(adminRoleService.getMenuIdsByRoleId(1L)).willReturn(List.of(1L, 2L, 3L));

        mockMvc.perform(get("/roles/1/menus").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(3));
    }

}
