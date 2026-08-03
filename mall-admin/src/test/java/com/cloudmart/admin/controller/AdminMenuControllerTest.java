package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.AdminMenuRequest;
import com.cloudmart.admin.dto.AdminMenuResponse;
import com.cloudmart.admin.service.AdminMenuService;
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

class AdminMenuControllerTest {

    private MockMvc mockMvc;
    private AdminMenuService adminMenuService;

    @BeforeEach
    void setUp() {
        adminMenuService = mock(AdminMenuService.class);
        AdminMenuController controller = new AdminMenuController(adminMenuService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void tree_returnsMenuTree() throws Exception {
        AdminMenuResponse menu = new AdminMenuResponse(
                1L, "系统管理", 0L, 1, "system", "system/index", "", "System",
                1, 0, "M", 0, 0, "system:list", "setting", null, LocalDateTime.now(), null
        );
        given(adminMenuService.tree()).willReturn(List.of(menu));

        mockMvc.perform(get("/menus/tree").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].menuName").value("系统管理"));
    }

    @Test
    void listByRoleId_returnsRoleMenus() throws Exception {
        AdminMenuResponse menu = new AdminMenuResponse(
                2L, "用户管理", 1L, 1, "user", "system/user", "", "User",
                1, 0, "C", 0, 0, "system:user:list", "user", null, LocalDateTime.now(), null
        );
        given(adminMenuService.listByRoleId(1L)).willReturn(List.of(menu));

        mockMvc.perform(get("/menus/role/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].menuName").value("用户管理"));
    }

    @Test
    void create_menuCreatedSuccessfully() throws Exception {
        doNothing().when(adminMenuService).create(any(AdminMenuRequest.class));

        mockMvc.perform(post("/menus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuName\":\"新菜单\",\"parentId\":0,\"menuType\":\"C\",\"orderNum\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void update_menuUpdatedSuccessfully() throws Exception {
        doNothing().when(adminMenuService).update(anyLong(), any(AdminMenuRequest.class));

        mockMvc.perform(put("/menus/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuName\":\"更新菜单\",\"parentId\":0,\"menuType\":\"C\",\"orderNum\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void delete_menuDeletedSuccessfully() throws Exception {
        doNothing().when(adminMenuService).delete(1L);

        mockMvc.perform(delete("/menus/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
