package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.AdminDictDataRequest;
import com.cloudmart.admin.dto.AdminDictDataResponse;
import com.cloudmart.admin.service.AdminDictDataService;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminDictDataControllerTest {

    private MockMvc mockMvc;
    private AdminDictDataService adminDictDataService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        adminDictDataService = mock(AdminDictDataService.class);
        AdminDictDataController controller = new AdminDictDataController(adminDictDataService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("GET /dict/data/type/{dictType} - 按类型查询字典数据")
    class ListByTypeTests {

        @Test
        @DisplayName("返回指定类型的字典数据列表")
        void listByType_returnsDictDataList() throws Exception {
            AdminDictDataResponse response = new AdminDictDataResponse(
                    1L, "sys_status", 1, "启用", "0",
                    null, null, 1, 0, null, LocalDateTime.now());
            given(adminDictDataService.listByType("sys_status")).willReturn(List.of(response));

            mockMvc.perform(get("/dict/data/type/sys_status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[0].dictLabel").value("启用"))
                    .andExpect(jsonPath("$.data[0].dictValue").value("0"));

            verify(adminDictDataService).listByType("sys_status");
        }
    }

    @Nested
    @DisplayName("GET /dict/data/{id} - 查询字典数据详情")
    class GetByIdTests {

        @Test
        @DisplayName("返回指定ID的字典数据")
        void getById_returnsDictData() throws Exception {
            AdminDictDataResponse response = new AdminDictDataResponse(
                    1L, "sys_status", 1, "启用", "0",
                    null, null, 1, 0, null, LocalDateTime.now());
            given(adminDictDataService.getById(1L)).willReturn(response);

            mockMvc.perform(get("/dict/data/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.dictLabel").value("启用"));

            verify(adminDictDataService).getById(1L);
        }
    }

    @Nested
    @DisplayName("POST /dict/data - 新增字典数据")
    class CreateTests {

        @Test
        @DisplayName("创建字典数据成功")
        void create_dictDataCreatedSuccessfully() throws Exception {
            doNothing().when(adminDictDataService).create(any(AdminDictDataRequest.class));

            AdminDictDataRequest request = new AdminDictDataRequest(
                    "sys_status", 1, "启用", "0", null, null, 1, 0, null);

            mockMvc.perform(post("/dict/data")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(adminDictDataService).create(any(AdminDictDataRequest.class));
        }
    }

    @Nested
    @DisplayName("PUT /dict/data/{id} - 修改字典数据")
    class UpdateTests {

        @Test
        @DisplayName("更新字典数据成功")
        void update_dictDataUpdatedSuccessfully() throws Exception {
            doNothing().when(adminDictDataService).update(anyLong(), any(AdminDictDataRequest.class));

            AdminDictDataRequest request = new AdminDictDataRequest(
                    "sys_status", 2, "禁用", "1", null, null, 0, 0, null);

            mockMvc.perform(put("/dict/data/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(adminDictDataService).update(anyLong(), any(AdminDictDataRequest.class));
        }
    }

    @Nested
    @DisplayName("DELETE /dict/data/{id} - 删除字典数据")
    class DeleteTests {

        @Test
        @DisplayName("删除字典数据成功")
        void delete_dictDataDeletedSuccessfully() throws Exception {
            doNothing().when(adminDictDataService).delete(1L);

            mockMvc.perform(delete("/dict/data/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(adminDictDataService).delete(1L);
        }
    }
}
