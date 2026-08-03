package com.cloudmart.community.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.community.service.GrowthService;
import com.cloudmart.community.vo.CheckInResultVO;
import com.cloudmart.community.vo.ExpLogVO;
import com.cloudmart.community.vo.LevelConfigVO;
import com.cloudmart.community.vo.UserLevelVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GrowthControllerTest {

    private MockMvc mockMvc;

    private final GrowthService growthService = Mockito.mock(GrowthService.class);

    private static final String USER_ID_HEADER = "X-User-Id";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new GrowthController(growthService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /growth/check-in - 每日签到成功")
    void checkIn_ShouldReturnSuccess() throws Exception {
        CheckInResultVO vo = new CheckInResultVO(true, 3, 10, 150L, 2, "活跃会员", "https://icon.example.com/lv2.png");
        given(growthService.checkIn(1L)).willReturn(vo);

        mockMvc.perform(post("/growth/check-in")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.checkedIn").value(true))
                .andExpect(jsonPath("$.data.continuousDays").value(3))
                .andExpect(jsonPath("$.data.expReward").value(10))
                .andExpect(jsonPath("$.data.levelTitle").value("活跃会员"));
    }

    @Test
    @DisplayName("POST /growth/check-in - 缺少X-User-Id头返回401")
    void checkIn_WithoutUserId_ShouldReturn401() throws Exception {
        mockMvc.perform(post("/growth/check-in"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /growth/check-in/status - 已签到返回true")
    void isCheckedInToday_CheckedIn_ShouldReturnTrue() throws Exception {
        given(growthService.isCheckedInToday(1L)).willReturn(true);

        mockMvc.perform(get("/growth/check-in/status")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    @DisplayName("GET /growth/check-in/status - 未签到返回false")
    void isCheckedInToday_NotCheckedIn_ShouldReturnFalse() throws Exception {
        given(growthService.isCheckedInToday(1L)).willReturn(false);

        mockMvc.perform(get("/growth/check-in/status")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(false));
    }

    @Test
    @DisplayName("GET /growth/check-in/calendar - 获取签到日历成功")
    void getCheckInCalendar_ShouldReturnSuccess() throws Exception {
        List<LocalDate> calendar = List.of(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 2));
        given(growthService.getCheckInCalendar(1L, 2026, 5)).willReturn(calendar);

        mockMvc.perform(get("/growth/check-in/calendar")
                        .header(USER_ID_HEADER, 1)
                        .param("year", "2026")
                        .param("month", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0]").value("2026-05-01"));
    }

    @Test
    @DisplayName("GET /growth/check-in/continuous - 获取连续签到天数成功")
    void getContinuousDays_ShouldReturnSuccess() throws Exception {
        given(growthService.getContinuousDays(1L)).willReturn(5);

        mockMvc.perform(get("/growth/check-in/continuous")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(5));
    }

    @Test
    @DisplayName("GET /growth/level - 获取用户等级信息成功")
    void getUserLevel_ShouldReturnSuccess() throws Exception {
        UserLevelVO vo = new UserLevelVO(1L, 3, 50, 350L, "高级会员",
                "https://icon.example.com/lv3.png", 200, "资深会员", 0.75);
        given(growthService.getUserLevel(1L)).willReturn(vo);

        mockMvc.perform(get("/growth/level")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.level").value(3))
                .andExpect(jsonPath("$.data.levelTitle").value("高级会员"))
                .andExpect(jsonPath("$.data.expProgress").value(0.75));
    }

    @Test
    @DisplayName("GET /growth/exp-logs - 获取经验值记录成功")
    void getExpLogs_ShouldReturnSuccess() throws Exception {
        ExpLogVO logVO = new ExpLogVO(1L, 10, "CHECK_IN", null, "每日签到", LocalDateTime.now());
        Page<ExpLogVO> page = new Page<>(1, 20, 1L);
        page.setRecords(List.of(logVO));
        given(growthService.getExpLogs(1L, 1, 20)).willReturn(page);

        mockMvc.perform(get("/growth/exp-logs")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].expChange").value(10))
                .andExpect(jsonPath("$.data[0].source").value("CHECK_IN"))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    @DisplayName("GET /growth/level-configs - 获取等级配置列表成功（公开接口）")
    void getLevelConfigs_ShouldReturnSuccess() throws Exception {
        LevelConfigVO configVO = new LevelConfigVO(1L, 1, "新手会员", 0,
                "https://icon.example.com/lv1.png", "基础权益", 1);
        given(growthService.getLevelConfigs()).willReturn(List.of(configVO));

        mockMvc.perform(get("/growth/level-configs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].level").value(1))
                .andExpect(jsonPath("$.data[0].title").value("新手会员"));
    }
}
