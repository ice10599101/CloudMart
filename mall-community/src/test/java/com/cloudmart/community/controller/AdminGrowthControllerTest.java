package com.cloudmart.community.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.community.dto.CreateLevelConfigRequest;
import com.cloudmart.community.dto.UpdateLevelConfigRequest;
import com.cloudmart.community.service.AdminGrowthService;
import com.cloudmart.community.vo.LevelConfigVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminGrowthControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AdminGrowthService adminGrowthService = Mockito.mock(AdminGrowthService.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminGrowthController(adminGrowthService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private LevelConfigVO buildLevelConfigVO() {
        return new LevelConfigVO(1L, 1, "新手", 0, "icon-level1", "基础权益", 1);
    }

    @Nested
    @DisplayName("GET /admin/growth/level-configs - 等级配置列表")
    class ListLevelConfigs {

        @Test
        @DisplayName("分页查询等级配置成功")
        void shouldReturnPagedLevelConfigs() throws Exception {
            LevelConfigVO vo = buildLevelConfigVO();
            Page<LevelConfigVO> page = new Page<>(1, 20, 1L);
            page.setRecords(List.of(vo));
            given(adminGrowthService.listLevelConfigs(1, 20)).willReturn(page);

            mockMvc.perform(get("/admin/growth/level-configs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].id").value(1))
                    .andExpect(jsonPath("$.data[0].title").value("新手"))
                    .andExpect(jsonPath("$.meta.page").value(1))
                    .andExpect(jsonPath("$.meta.total").value(1));
        }
    }

    @Nested
    @DisplayName("POST /admin/growth/level-configs - 创建等级配置")
    class CreateLevelConfig {

        @Test
        @DisplayName("创建等级配置成功")
        void shouldCreateLevelConfig() throws Exception {
            CreateLevelConfigRequest request = new CreateLevelConfigRequest(2, "进阶", 100, "icon-level2", "进阶权益");
            LevelConfigVO vo = new LevelConfigVO(2L, 2, "进阶", 100, "icon-level2", "进阶权益", 1);
            given(adminGrowthService.createLevelConfig(any(CreateLevelConfigRequest.class))).willReturn(vo);

            mockMvc.perform(post("/admin/growth/level-configs")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(2))
                    .andExpect(jsonPath("$.data.title").value("进阶"));
        }
    }

    @Nested
    @DisplayName("PUT /admin/growth/level-configs/{id} - 更新等级配置")
    class UpdateLevelConfig {

        @Test
        @DisplayName("更新等级配置成功")
        void shouldUpdateLevelConfig() throws Exception {
            UpdateLevelConfigRequest request = new UpdateLevelConfigRequest("更新标题", 200, null, null, 1);
            LevelConfigVO vo = new LevelConfigVO(1L, 1, "更新标题", 200, "icon-level1", "基础权益", 1);
            given(adminGrowthService.updateLevelConfig(eq(1L), any(UpdateLevelConfigRequest.class))).willReturn(vo);

            mockMvc.perform(put("/admin/growth/level-configs/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.title").value("更新标题"));
        }
    }

    @Nested
    @DisplayName("DELETE /admin/growth/level-configs/{id} - 删除等级配置")
    class DeleteLevelConfig {

        @Test
        @DisplayName("删除等级配置成功")
        void shouldDeleteLevelConfig() throws Exception {
            willDoNothing().given(adminGrowthService).deleteLevelConfig(1L);

            mockMvc.perform(delete("/admin/growth/level-configs/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("GET /admin/growth/stats - 成长体系统计")
    class GetStats {

        @Test
        @DisplayName("获取成长体系统计成功")
        void shouldReturnGrowthStats() throws Exception {
            given(adminGrowthService.getTotalCheckIns()).willReturn(1000L);
            given(adminGrowthService.getTodayCheckIns()).willReturn(50L);

            mockMvc.perform(get("/admin/growth/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.totalCheckIns").value(1000))
                    .andExpect(jsonPath("$.data.todayCheckIns").value(50));
        }
    }
}
