package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.AdminPostRequest;
import com.cloudmart.admin.dto.AdminPostResponse;
import com.cloudmart.admin.service.AdminPostService;
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

class AdminPostControllerTest {

    private MockMvc mockMvc;
    private AdminPostService adminPostService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        adminPostService = mock(AdminPostService.class);
        AdminPostController controller = new AdminPostController(adminPostService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("GET /posts - 岗位列表")
    class ListTests {

        @Test
        @DisplayName("返回所有岗位列表")
        void list_returnsAllPosts() throws Exception {
            AdminPostResponse response = new AdminPostResponse(
                    1L, "CEO", "首席执行官", 1, 0, null, LocalDateTime.now());
            given(adminPostService.list()).willReturn(List.of(response));

            mockMvc.perform(get("/posts"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[0].postCode").value("CEO"))
                    .andExpect(jsonPath("$.data[0].postName").value("首席执行官"));

            verify(adminPostService).list();
        }
    }

    @Nested
    @DisplayName("GET /posts/{id} - 查询岗位详情")
    class GetByIdTests {

        @Test
        @DisplayName("返回指定ID的岗位详情")
        void getById_returnsPostDetail() throws Exception {
            AdminPostResponse response = new AdminPostResponse(
                    1L, "CEO", "首席执行官", 1, 0, null, LocalDateTime.now());
            given(adminPostService.getById(1L)).willReturn(response);

            mockMvc.perform(get("/posts/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.postCode").value("CEO"));

            verify(adminPostService).getById(1L);
        }
    }

    @Nested
    @DisplayName("POST /posts - 新增岗位")
    class CreateTests {

        @Test
        @DisplayName("创建岗位成功")
        void create_postCreatedSuccessfully() throws Exception {
            doNothing().when(adminPostService).create(any(AdminPostRequest.class));

            AdminPostRequest request = new AdminPostRequest("CTO", "首席技术官", 2, 0, null);

            mockMvc.perform(post("/posts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(adminPostService).create(any(AdminPostRequest.class));
        }
    }

    @Nested
    @DisplayName("PUT /posts/{id} - 修改岗位")
    class UpdateTests {

        @Test
        @DisplayName("更新岗位成功")
        void update_postUpdatedSuccessfully() throws Exception {
            doNothing().when(adminPostService).update(anyLong(), any(AdminPostRequest.class));

            AdminPostRequest request = new AdminPostRequest("CTO", "首席技术官", 3, 0, "updated");

            mockMvc.perform(put("/posts/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(adminPostService).update(anyLong(), any(AdminPostRequest.class));
        }
    }

    @Nested
    @DisplayName("DELETE /posts/{id} - 删除岗位")
    class DeleteTests {

        @Test
        @DisplayName("删除岗位成功")
        void delete_postDeletedSuccessfully() throws Exception {
            doNothing().when(adminPostService).delete(1L);

            mockMvc.perform(delete("/posts/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(adminPostService).delete(1L);
        }
    }
}
