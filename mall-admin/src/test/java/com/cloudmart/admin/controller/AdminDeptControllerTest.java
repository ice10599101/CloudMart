package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.AdminDeptRequest;
import com.cloudmart.admin.dto.AdminDeptResponse;
import com.cloudmart.admin.service.AdminDeptService;
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

class AdminDeptControllerTest {

    private MockMvc mockMvc;
    private AdminDeptService adminDeptService;

    @BeforeEach
    void setUp() {
        adminDeptService = mock(AdminDeptService.class);
        AdminDeptController controller = new AdminDeptController(adminDeptService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void tree_returnsDeptTree() throws Exception {
        AdminDeptResponse dept = new AdminDeptResponse(
                1L, 0L, "0", "总公司", 1, "admin", "13800000001", "admin@test.com", 0, LocalDateTime.now(), null
        );
        given(adminDeptService.tree()).willReturn(List.of(dept));

        mockMvc.perform(get("/depts/tree").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].deptName").value("总公司"));
    }

    @Test
    void getById_returnsDeptDetail() throws Exception {
        AdminDeptResponse dept = new AdminDeptResponse(
                1L, 0L, "0", "总公司", 1, "admin", "13800000001", "admin@test.com", 0, LocalDateTime.now(), null
        );
        given(adminDeptService.getById(1L)).willReturn(dept);

        mockMvc.perform(get("/depts/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.deptName").value("总公司"));
    }

    @Test
    void create_deptCreatedSuccessfully() throws Exception {
        doNothing().when(adminDeptService).create(any(AdminDeptRequest.class));

        mockMvc.perform(post("/depts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deptName\":\"技术部\",\"parentId\":1,\"orderNum\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void update_deptUpdatedSuccessfully() throws Exception {
        doNothing().when(adminDeptService).update(anyLong(), any(AdminDeptRequest.class));

        mockMvc.perform(put("/depts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deptName\":\"研发部\",\"parentId\":1,\"orderNum\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void delete_deptDeletedSuccessfully() throws Exception {
        doNothing().when(adminDeptService).delete(1L);

        mockMvc.perform(delete("/depts/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
