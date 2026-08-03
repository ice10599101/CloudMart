package com.cloudmart.community.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.community.dto.CreateTagRequest;
import com.cloudmart.community.dto.UpdateTagRequest;
import com.cloudmart.community.service.TagService;
import com.cloudmart.community.vo.TagVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminTagControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TagService tagService = Mockito.mock(TagService.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminTagController(tagService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private TagVO buildTagVO() {
        return new TagVO(1L, "技术", "icon-tech", 5, true, 1, LocalDateTime.now());
    }

    @Nested
    @DisplayName("GET /admin/tags - 标签列表")
    class ListTags {

        @Test
        @DisplayName("分页查询标签列表成功")
        void shouldReturnPagedTags() throws Exception {
            TagVO vo = buildTagVO();
            Page<TagVO> page = new Page<>(1, 20, 1L);
            page.setRecords(List.of(vo));
            given(tagService.listTags(1, 20)).willReturn(page);

            mockMvc.perform(get("/admin/tags"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].id").value(1))
                    .andExpect(jsonPath("$.data[0].name").value("技术"))
                    .andExpect(jsonPath("$.meta.page").value(1))
                    .andExpect(jsonPath("$.meta.total").value(1));
        }
    }

    @Nested
    @DisplayName("POST /admin/tags - 创建标签")
    class CreateTag {

        @Test
        @DisplayName("创建标签成功")
        void shouldCreateTag() throws Exception {
            CreateTagRequest request = new CreateTagRequest("新技术", "icon-new");
            TagVO vo = new TagVO(2L, "新技术", "icon-new", 0, false, 1, LocalDateTime.now());
            given(tagService.createTag(any(CreateTagRequest.class))).willReturn(vo);

            mockMvc.perform(post("/admin/tags")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(2))
                    .andExpect(jsonPath("$.data.name").value("新技术"));
        }
    }

    @Nested
    @DisplayName("PUT /admin/tags/{id} - 更新标签")
    class UpdateTag {

        @Test
        @DisplayName("更新标签成功")
        void shouldUpdateTag() throws Exception {
            UpdateTagRequest request = new UpdateTagRequest("更新标签", "icon-updated", 1);
            TagVO vo = new TagVO(1L, "更新标签", "icon-updated", 5, true, 1, LocalDateTime.now());
            given(tagService.updateTag(eq(1L), any(UpdateTagRequest.class))).willReturn(vo);

            mockMvc.perform(put("/admin/tags/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.name").value("更新标签"));
        }
    }

    @Nested
    @DisplayName("DELETE /admin/tags/{id} - 删除标签")
    class DeleteTag {

        @Test
        @DisplayName("删除标签成功")
        void shouldDeleteTag() throws Exception {
            willDoNothing().given(tagService).deleteTag(1L);

            mockMvc.perform(delete("/admin/tags/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }
}
