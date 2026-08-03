package com.cloudmart.live.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.live.dto.LiveRoomDTO;
import com.cloudmart.live.feign.SeckillFeignClient;
import com.cloudmart.live.service.LiveRoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LiveSeckillControllerTest {

    private MockMvc mockMvc;

    private final LiveRoomService liveRoomService = Mockito.mock(LiveRoomService.class);
    private final SeckillFeignClient seckillFeignClient = Mockito.mock(SeckillFeignClient.class);

    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 5, 29, 10, 0);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new LiveSeckillController(liveRoomService, seckillFeignClient))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("直播间秒杀 - 成功执行返回信封")
    void executeLiveSeckill_WhenRoomLiveAndHasActivity_ShouldReturnEnvelope() throws Exception {
        LiveRoomDTO dto = new LiveRoomDTO(1L, "直播1", "描述", 100L, "主播A",
                "cover.jpg", "rtmp://stream", 200L, 300L, 1000, 50, 1000L,
                "LIVE", FIXED_TIME, null, FIXED_TIME);

        given(liveRoomService.getRoom(1L)).willReturn(dto);
        given(seckillFeignClient.executeSeckill(1L, 300L))
                .willReturn(ApiResponse.ok(Map.of("orderId", 999L, "status", "SUCCESS")));

        mockMvc.perform(post("/seckill/rooms/1/execute")
                        .header("X-User-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderId").value(999));
    }

    @Test
    @DisplayName("直播间秒杀 - 直播间未在直播中返回错误信封")
    void executeLiveSeckill_WhenRoomNotLive_ShouldReturnErrorEnvelope() throws Exception {
        LiveRoomDTO dto = new LiveRoomDTO(1L, "直播1", "描述", 100L, "主播A",
                "cover.jpg", "rtmp://stream", null, 300L, 1000, 50, 1000L,
                "OFFLINE", null, null, FIXED_TIME);

        given(liveRoomService.getRoom(1L)).willReturn(dto);

        mockMvc.perform(post("/seckill/rooms/1/execute")
                        .header("X-User-Id", 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ROOM_NOT_LIVE"));
    }

    @Test
    @DisplayName("直播间秒杀 - 无秒杀活动返回错误信封")
    void executeLiveSeckill_WhenNoSeckillActivity_ShouldReturnErrorEnvelope() throws Exception {
        LiveRoomDTO dto = new LiveRoomDTO(1L, "直播1", "描述", 100L, "主播A",
                "cover.jpg", "rtmp://stream", null, null, 1000, 50, 1000L,
                "LIVE", FIXED_TIME, null, FIXED_TIME);

        given(liveRoomService.getRoom(1L)).willReturn(dto);

        mockMvc.perform(post("/seckill/rooms/1/execute")
                        .header("X-User-Id", 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NO_SECKILL_ACTIVITY"));
    }

    @Test
    @DisplayName("获取直播间秒杀活动 - 成功返回信封")
    void getLiveSeckillActivity_WhenHasActivity_ShouldReturnEnvelope() throws Exception {
        LiveRoomDTO dto = new LiveRoomDTO(1L, "直播1", "描述", 100L, "主播A",
                "cover.jpg", "rtmp://stream", null, 300L, 1000, 50, 1000L,
                "LIVE", FIXED_TIME, null, FIXED_TIME);

        given(liveRoomService.getRoom(1L)).willReturn(dto);
        given(seckillFeignClient.getSeckillActivity(300L, "mall-live"))
                .willReturn(ApiResponse.ok(Map.of("activityId", 300L, "status", "ACTIVE")));

        mockMvc.perform(get("/seckill/rooms/1/activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.activityId").value(300));
    }

    @Test
    @DisplayName("获取直播间秒杀活动 - 无活动返回错误信封")
    void getLiveSeckillActivity_WhenNoActivity_ShouldReturnErrorEnvelope() throws Exception {
        LiveRoomDTO dto = new LiveRoomDTO(1L, "直播1", "描述", 100L, "主播A",
                "cover.jpg", "rtmp://stream", null, null, 1000, 50, 1000L,
                "LIVE", FIXED_TIME, null, FIXED_TIME);

        given(liveRoomService.getRoom(1L)).willReturn(dto);

        mockMvc.perform(get("/seckill/rooms/1/activity"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NO_SECKILL_ACTIVITY"));
    }
}
