package com.cloudmart.seckill.controller;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.seckill.converter.SeckillConverter;
import com.cloudmart.seckill.dto.CreateActivityRequest;
import com.cloudmart.seckill.dto.SeckillActivityDTO;
import com.cloudmart.seckill.service.SeckillActivityService;
import com.cloudmart.seckill.vo.SeckillActivityVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeckillActivityControllerTest {

    private MockMvc mockMvc;

    private final SeckillActivityService activityService = Mockito.mock(SeckillActivityService.class);
    private final SeckillConverter seckillConverter = Mockito.mock(SeckillConverter.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SeckillActivityController(activityService, seckillConverter))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("创建秒杀活动 - 成功返回信封格式")
    void createActivity_ShouldReturn200WithEnvelope() throws Exception {
        LocalDateTime startTime = LocalDateTime.now().plusDays(1);
        LocalDateTime endTime = LocalDateTime.now().plusDays(2);
        SeckillActivityDTO dto = new SeckillActivityDTO(1L, "双十一秒杀", "限时秒杀",
                startTime, endTime, "PENDING", LocalDateTime.now());

        given(activityService.createActivity(Mockito.any(CreateActivityRequest.class))).willReturn(dto);

        SeckillActivityVO vo = new SeckillActivityVO(1L, "双十一秒杀", startTime, endTime, "PENDING");
        given(seckillConverter.activityDtoToVO(dto)).willReturn(vo);

        mockMvc.perform(post("/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"双十一秒杀\",\"description\":\"限时秒杀\",\"startTime\":\"2026-06-01T00:00:00\",\"endTime\":\"2026-06-02T00:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("双十一秒杀"));
    }

    @Test
    @DisplayName("查询秒杀活动列表 - 成功返回信封格式")
    void listActivities_ShouldReturn200WithEnvelope() throws Exception {
        SeckillActivityDTO dto = new SeckillActivityDTO(1L, "双十一秒杀", "限时秒杀",
                LocalDateTime.now(), LocalDateTime.now().plusDays(1), "ACTIVE", LocalDateTime.now());

        given(activityService.listActivities(null)).willReturn(List.of(dto));

        SeckillActivityVO vo = new SeckillActivityVO(1L, "双十一秒杀", LocalDateTime.now(), LocalDateTime.now().plusDays(1), "ACTIVE");
        given(seckillConverter.activityDtoListToVOList(List.of(dto))).willReturn(List.of(vo));

        mockMvc.perform(get("/activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1));
    }

    @Test
    @DisplayName("查询秒杀活动详情 - 成功返回信封格式")
    void getActivity_ShouldReturn200WithEnvelope() throws Exception {
        SeckillActivityDTO dto = new SeckillActivityDTO(1L, "双十一秒杀", "限时秒杀",
                LocalDateTime.now(), LocalDateTime.now().plusDays(1), "ACTIVE", LocalDateTime.now());

        given(activityService.getActivity(1L)).willReturn(dto);

        SeckillActivityVO vo = new SeckillActivityVO(1L, "双十一秒杀", LocalDateTime.now(), LocalDateTime.now().plusDays(1), "ACTIVE");
        given(seckillConverter.activityDtoToVO(dto)).willReturn(vo);

        mockMvc.perform(get("/activities/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("更新活动状态 - 成功返回信封格式")
    void updateActivityStatus_ShouldReturn200WithEnvelope() throws Exception {
        SeckillActivityDTO dto = new SeckillActivityDTO(1L, "双十一秒杀", "限时秒杀",
                LocalDateTime.now(), LocalDateTime.now().plusDays(1), "ACTIVE", LocalDateTime.now());

        given(activityService.updateActivityStatus(1L, "ACTIVE")).willReturn(dto);

        SeckillActivityVO vo = new SeckillActivityVO(1L, "双十一秒杀", LocalDateTime.now(), LocalDateTime.now().plusDays(1), "ACTIVE");
        given(seckillConverter.activityDtoToVO(dto)).willReturn(vo);

        mockMvc.perform(put("/activities/1/status")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("查询不存在的秒杀活动 - 返回错误信封")
    void getActivity_WhenNotFound_ShouldReturnErrorEnvelope() throws Exception {
        given(activityService.getActivity(999L))
                .willThrow(new BusinessException("ACTIVITY_NOT_FOUND", "秒杀活动不存在"));

        mockMvc.perform(get("/activities/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ACTIVITY_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("秒杀活动不存在"));
    }

    @Test
    @DisplayName("创建秒杀活动 - 缺少必填字段返回校验错误")
    void createActivity_WhenMissingRequiredFields_ShouldReturnValidationError() throws Exception {
        mockMvc.perform(post("/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
