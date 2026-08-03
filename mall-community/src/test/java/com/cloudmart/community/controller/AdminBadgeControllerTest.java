package com.cloudmart.community.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.community.dto.CreateBadgeRequest;
import com.cloudmart.community.dto.UpdateBadgeRequest;
import com.cloudmart.community.service.BadgeService;
import com.cloudmart.community.vo.BadgeVO;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminBadgeControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BadgeService badgeService = Mockito.mock(BadgeService.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminBadgeController(badgeService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private BadgeVO buildBadgeVO() {
        return new BadgeVO(1L, "优质作者", "icon-badge", "优质内容创作者", "发帖>100", 2, 1, LocalDateTime.now());
    }

    @Nested
    @DisplayName("GET /admin/badges - 徽章列表")
    class ListBadges {

        @Test
        @DisplayName("分页查询徽章列表成功")
        void shouldReturnPagedBadges() throws Exception {
            BadgeVO vo = buildBadgeVO();
            Page<BadgeVO> page = new Page<>(1, 20, 1L);
            page.setRecords(List.of(vo));
            given(badgeService.listBadges(1, 20)).willReturn(page);

            mockMvc.perform(get("/admin/badges"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].id").value(1))
                    .andExpect(jsonPath("$.data[0].name").value("优质作者"))
                    .andExpect(jsonPath("$.meta.page").value(1))
                    .andExpect(jsonPath("$.meta.total").value(1));
        }
    }

    @Nested
    @DisplayName("POST /admin/badges - 创建徽章")
    class CreateBadge {

        @Test
        @DisplayName("创建徽章成功")
        void shouldCreateBadge() throws Exception {
            CreateBadgeRequest request = new CreateBadgeRequest("新徽章", "icon-new", "描述", null, 1);
            BadgeVO vo = new BadgeVO(2L, "新徽章", "icon-new", "描述", null, 1, 1, LocalDateTime.now());
            given(badgeService.createBadge(any(CreateBadgeRequest.class))).willReturn(vo);

            mockMvc.perform(post("/admin/badges")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(2))
                    .andExpect(jsonPath("$.data.name").value("新徽章"));
        }
    }

    @Nested
    @DisplayName("PUT /admin/badges/{id} - 更新徽章")
    class UpdateBadge {

        @Test
        @DisplayName("更新徽章成功")
        void shouldUpdateBadge() throws Exception {
            UpdateBadgeRequest request = new UpdateBadgeRequest("更新徽章", "icon-updated", "更新描述", null, 2, 1);
            BadgeVO vo = new BadgeVO(1L, "更新徽章", "icon-updated", "更新描述", null, 2, 1, LocalDateTime.now());
            given(badgeService.updateBadge(eq(1L), any(UpdateBadgeRequest.class))).willReturn(vo);

            mockMvc.perform(put("/admin/badges/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.name").value("更新徽章"));
        }
    }

    @Nested
    @DisplayName("DELETE /admin/badges/{id} - 删除徽章")
    class DeleteBadge {

        @Test
        @DisplayName("删除徽章成功")
        void shouldDeleteBadge() throws Exception {
            willDoNothing().given(badgeService).deleteBadge(1L);

            mockMvc.perform(delete("/admin/badges/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("POST /admin/badges/{id}/grant - 授予徽章")
    class GrantBadge {

        @Test
        @DisplayName("授予徽章成功")
        void shouldGrantBadge() throws Exception {
            willDoNothing().given(badgeService).grantBadge(2L, 1L);

            mockMvc.perform(post("/admin/badges/1/grant")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("userId", 2))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("POST /admin/badges/{id}/revoke - 撤销徽章")
    class RevokeBadge {

        @Test
        @DisplayName("撤销徽章成功")
        void shouldRevokeBadge() throws Exception {
            willDoNothing().given(badgeService).revokeBadge(2L, 1L);

            mockMvc.perform(post("/admin/badges/1/revoke")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("userId", 2))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }
}
