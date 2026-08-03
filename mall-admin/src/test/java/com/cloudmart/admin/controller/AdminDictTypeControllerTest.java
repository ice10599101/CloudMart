package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.AdminDictTypeRequest;
import com.cloudmart.admin.dto.AdminDictTypeResponse;
import com.cloudmart.admin.service.AdminDictTypeService;
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

class AdminDictTypeControllerTest {

    private MockMvc mockMvc;
    private AdminDictTypeService adminDictTypeService;

    @BeforeEach
    void setUp() {
        adminDictTypeService = mock(AdminDictTypeService.class);
        AdminDictTypeController controller = new AdminDictTypeController(adminDictTypeService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void list_returnsAllDictTypes() throws Exception {
        AdminDictTypeResponse dictType = new AdminDictTypeResponse(1L, "系统状态", "sys_status", 0, null, LocalDateTime.now());
        given(adminDictTypeService.list()).willReturn(List.of(dictType));

        mockMvc.perform(get("/dict/types").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].dictType").value("sys_status"));
    }

    @Test
    void getById_returnsDictTypeDetail() throws Exception {
        AdminDictTypeResponse dictType = new AdminDictTypeResponse(1L, "系统状态", "sys_status", 0, null, LocalDateTime.now());
        given(adminDictTypeService.getById(1L)).willReturn(dictType);

        mockMvc.perform(get("/dict/types/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void create_dictTypeCreatedSuccessfully() throws Exception {
        doNothing().when(adminDictTypeService).create(any(AdminDictTypeRequest.class));

        mockMvc.perform(post("/dict/types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dictName\":\"系统状态\",\"dictType\":\"sys_status\",\"status\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void update_dictTypeUpdatedSuccessfully() throws Exception {
        doNothing().when(adminDictTypeService).update(anyLong(), any(AdminDictTypeRequest.class));

        mockMvc.perform(put("/dict/types/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dictName\":\"状态管理\",\"dictType\":\"sys_status\",\"status\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void delete_dictTypeDeletedSuccessfully() throws Exception {
        doNothing().when(adminDictTypeService).delete(1L);

        mockMvc.perform(delete("/dict/types/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void refreshCache_cacheRefreshedSuccessfully() throws Exception {
        doNothing().when(adminDictTypeService).refreshCache();

        mockMvc.perform(put("/dict/types/cache/refresh").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
