package com.cloudmart.seckill.controller;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.seckill.converter.SeckillConverter;
import com.cloudmart.seckill.dto.CreateActivityRequest;
import com.cloudmart.seckill.dto.SeckillActivityDTO;
import com.cloudmart.seckill.repository.SeckillActivityMapper;
import com.cloudmart.seckill.service.SeckillActivityService;
import com.cloudmart.seckill.vo.SeckillActivityVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminSeckillActivityControllerTest {

    private MockMvc mockMvc;

    private final SeckillActivityService activityService = Mockito.mock(SeckillActivityService.class);
    private final SeckillConverter seckillConverter = Mockito.mock(SeckillConverter.class);
    private final SeckillActivityMapper seckillActivityMapper = Mockito.mock(SeckillActivityMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final LocalDateTime START = LocalDateTime.of(2026, 6, 1, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 6, 2, 0, 0);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminSeckillActivityController(activityService, seckillConverter, seckillActivityMapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("GET /admin/seckill/activities")
    class ListActivitiesTests {

        @Test
        @DisplayName("查询秒杀活动列表成功返回信封格式")
        void listActivities_ShouldReturnSuccessEnvelope() throws Exception {
            SeckillActivityDTO dto = new SeckillActivityDTO(1L, "双十一秒杀", "限时秒杀", START, END, "UPCOMING", START);
            given(activityService.listActivities(null)).willReturn(List.of(dto));

            SeckillActivityVO vo = new SeckillActivityVO(1L, "双十一秒杀", START, END, "UPCOMING");
            given(seckillConverter.activityDtoListToVOList(List.of(dto))).willReturn(List.of(vo));

            mockMvc.perform(get("/admin/seckill/activities"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].id").value(1))
                    .andExpect(jsonPath("$.data[0].name").value("双十一秒杀"))
                    .andExpect(jsonPath("$.data[0].status").value("UPCOMING"));
        }

        @Test
        @DisplayName("按状态查询秒杀活动列表成功返回信封格式")
        void listActivities_WithStatus_ShouldReturnFilteredEnvelope() throws Exception {
            SeckillActivityDTO dto = new SeckillActivityDTO(1L, "双十一秒杀", "限时秒杀", START, END, "UPCOMING", START);
            given(activityService.listActivities("UPCOMING")).willReturn(List.of(dto));

            SeckillActivityVO vo = new SeckillActivityVO(1L, "双十一秒杀", START, END, "UPCOMING");
            given(seckillConverter.activityDtoListToVOList(List.of(dto))).willReturn(List.of(vo));

            mockMvc.perform(get("/admin/seckill/activities")
                            .param("status", "UPCOMING"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].status").value("UPCOMING"));
        }
    }

    @Nested
    @DisplayName("GET /admin/seckill/activities/{activityId}")
    class GetActivityTests {

        @Test
        @DisplayName("查询秒杀活动详情成功返回信封格式")
        void getActivity_ShouldReturnSuccessEnvelope() throws Exception {
            SeckillActivityDTO dto = new SeckillActivityDTO(1L, "双十一秒杀", "限时秒杀", START, END, "UPCOMING", START);
            given(activityService.getActivity(1L)).willReturn(dto);

            SeckillActivityVO vo = new SeckillActivityVO(1L, "双十一秒杀", START, END, "UPCOMING");
            given(seckillConverter.activityDtoToVO(dto)).willReturn(vo);

            mockMvc.perform(get("/admin/seckill/activities/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.name").value("双十一秒杀"));
        }

        @Test
        @DisplayName("活动不存在返回错误信封")
        void getActivity_WhenNotFound_ShouldReturnErrorEnvelope() throws Exception {
            willThrow(new BusinessException("ACTIVITY_NOT_FOUND", "活动不存在"))
                    .given(activityService).getActivity(999L);

            mockMvc.perform(get("/admin/seckill/activities/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("ACTIVITY_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("POST /admin/seckill/activities")
    class CreateActivityTests {

        @Test
        @DisplayName("创建秒杀活动成功返回信封格式")
        void createActivity_ShouldReturnSuccessEnvelope() throws Exception {
            SeckillActivityDTO dto = new SeckillActivityDTO(1L, "双十一秒杀", "限时秒杀", START, END, "UPCOMING", START);
            given(activityService.createActivity(Mockito.any(CreateActivityRequest.class))).willReturn(dto);

            SeckillActivityVO vo = new SeckillActivityVO(1L, "双十一秒杀", START, END, "UPCOMING");
            given(seckillConverter.activityDtoToVO(dto)).willReturn(vo);

            mockMvc.perform(post("/admin/seckill/activities")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"双十一秒杀\",\"description\":\"限时秒杀\",\"startTime\":\"2026-06-01T00:00:00\",\"endTime\":\"2026-06-02T00:00:00\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.name").value("双十一秒杀"));
        }

        @Test
        @DisplayName("创建秒杀活动缺少必填字段返回校验错误")
        void createActivity_WhenMissingRequiredFields_ShouldReturnValidationError() throws Exception {
            mockMvc.perform(post("/admin/seckill/activities")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
    }

    @Nested
    @DisplayName("PUT /admin/seckill/activities/{activityId}/status")
    class UpdateActivityStatusTests {

        @Test
        @DisplayName("更新活动状态成功返回信封格式")
        void updateActivityStatus_ShouldReturnSuccessEnvelope() throws Exception {
            SeckillActivityDTO dto = new SeckillActivityDTO(1L, "双十一秒杀", "限时秒杀", START, END, "ONGOING", START);
            given(activityService.updateActivityStatus(1L, "ONGOING")).willReturn(dto);

            SeckillActivityVO vo = new SeckillActivityVO(1L, "双十一秒杀", START, END, "ONGOING");
            given(seckillConverter.activityDtoToVO(dto)).willReturn(vo);

            mockMvc.perform(put("/admin/seckill/activities/1/status")
                            .param("status", "ONGOING"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("ONGOING"));
        }

        @Test
        @DisplayName("更新不存在活动的状态返回错误信封")
        void updateActivityStatus_WhenNotFound_ShouldReturnErrorEnvelope() throws Exception {
            willThrow(new BusinessException("ACTIVITY_NOT_FOUND", "活动不存在"))
                    .given(activityService).updateActivityStatus(999L, "ONGOING");

            mockMvc.perform(put("/admin/seckill/activities/999/status")
                            .param("status", "ONGOING"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("ACTIVITY_NOT_FOUND"));
        }
    }
}
