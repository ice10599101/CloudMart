package com.cloudmart.community.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.community.service.TagService;
import com.cloudmart.community.vo.TagVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TagControllerTest {

    private MockMvc mockMvc;

    private final TagService tagService = Mockito.mock(TagService.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TagController(tagService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private TagVO buildTagVO() {
        return new TagVO(1L, "技术", "https://icon.example.com/tech.png", 50, true, 1, LocalDateTime.now());
    }

    @Test
    @DisplayName("GET /tags/hot - 获取热门标签成功")
    void getHotTags_ShouldReturnSuccess() throws Exception {
        TagVO vo = buildTagVO();
        given(tagService.getHotTags()).willReturn(List.of(vo));

        mockMvc.perform(get("/tags/hot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("技术"))
                .andExpect(jsonPath("$.data[0].isHot").value(true));
    }

    @Test
    @DisplayName("GET /tags/trending - 获取热门话题排行成功")
    void getTrendingTopics_ShouldReturnSuccess() throws Exception {
        TagVO vo = buildTagVO();
        given(tagService.getTrendingTopics(10)).willReturn(List.of(vo));

        mockMvc.perform(get("/tags/trending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1));
    }

    @Test
    @DisplayName("GET /tags/trending - 自定义limit参数成功")
    void getTrendingTopics_WithCustomLimit_ShouldReturnSuccess() throws Exception {
        TagVO vo = buildTagVO();
        given(tagService.getTrendingTopics(5)).willReturn(List.of(vo));

        mockMvc.perform(get("/tags/trending")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("GET /tags - 分页获取标签列表成功")
    void listTags_ShouldReturnSuccess() throws Exception {
        TagVO vo = buildTagVO();
        Page<TagVO> page = new Page<>(1, 20, 1L);
        page.setRecords(List.of(vo));
        given(tagService.listTags(1, 20)).willReturn(page);

        mockMvc.perform(get("/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.pageSize").value(20))
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    @DisplayName("GET /tags/{id} - 获取标签详情成功")
    void getTagById_ShouldReturnSuccess() throws Exception {
        TagVO vo = buildTagVO();
        given(tagService.getTagById(1L)).willReturn(vo);

        mockMvc.perform(get("/tags/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("技术"));
    }
}
