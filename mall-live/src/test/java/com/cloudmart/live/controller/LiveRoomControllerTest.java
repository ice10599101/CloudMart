package com.cloudmart.live.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.live.converter.LiveConverter;
import com.cloudmart.live.dto.LiveRoomDTO;
import com.cloudmart.live.service.LiveRoomService;
import com.cloudmart.live.vo.LiveRoomVO;
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
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LiveRoomControllerTest {

    private MockMvc mockMvc;

    private final LiveRoomService liveRoomService = Mockito.mock(LiveRoomService.class);
    private final LiveConverter liveConverter = Mockito.mock(LiveConverter.class);

    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 5, 29, 10, 0);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new LiveRoomController(liveRoomService, liveConverter))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("获取直播间详情 - 成功返回信封格式")
    void getRoom_WhenExists_ShouldReturnEnvelope() throws Exception {
        LiveRoomDTO dto = new LiveRoomDTO(1L, "测试直播", "描述", 100L, "主播A",
                "cover.jpg", "rtmp://stream", 200L, null, 1000, 50, 1000L,
                "LIVE", FIXED_TIME, null, FIXED_TIME);
        LiveRoomVO vo = new LiveRoomVO(1L, "测试直播", "主播A", "cover.jpg",
                "LIVE", 200L, 50, FIXED_TIME, null);

        given(liveRoomService.getRoom(1L)).willReturn(dto);
        given(liveConverter.dtoToVO(dto)).willReturn(vo);

        mockMvc.perform(get("/rooms/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.title").value("测试直播"))
                .andExpect(jsonPath("$.data.status").value("LIVE"));
    }

    @Test
    @DisplayName("获取直播间详情 - 不存在时抛出BusinessException")
    void getRoom_WhenNotFound_ShouldReturnErrorEnvelope() throws Exception {
        given(liveRoomService.getRoom(999L))
                .willThrow(new BusinessException("LIVE_SERVICE_UNAVAILABLE", "直播间不存在"));

        mockMvc.perform(get("/rooms/999"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("LIVE_SERVICE_UNAVAILABLE"));
    }

    @Test
    @DisplayName("查询直播间列表 - 成功返回分页信封")
    void listRooms_ShouldReturnPagedEnvelope() throws Exception {
        LiveRoomDTO dto = new LiveRoomDTO(1L, "直播1", "描述", 100L, "主播A",
                "cover.jpg", "rtmp://stream", null, null, 1000, 50, 1000L,
                "LIVE", FIXED_TIME, null, FIXED_TIME);
        Page<LiveRoomDTO> page = new Page<>(1, 10, 1L);
        page.setRecords(List.of(dto));

        LiveRoomVO vo = new LiveRoomVO(1L, "直播1", "主播A", "cover.jpg",
                "LIVE", null, 50, FIXED_TIME, null);

        given(liveRoomService.listRooms(null, 1, 10)).willReturn(page);
        given(liveConverter.dtoToVO(dto)).willReturn(vo);

        mockMvc.perform(get("/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.records[0].id").value(1));
    }

    @Test
    @DisplayName("查询直播间列表 - 按状态筛选")
    void listRooms_WithStatusFilter_ShouldReturnFiltered() throws Exception {
        Page<LiveRoomDTO> page = new Page<>(1, 10, 0L);
        page.setRecords(List.of());

        given(liveRoomService.listRooms("OFFLINE", 1, 10)).willReturn(page);

        mockMvc.perform(get("/rooms").param("status", "OFFLINE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    @DisplayName("进入直播间 - 成功递增观众数并返回信封")
    void enterRoom_ShouldIncrementViewerAndReturnEnvelope() throws Exception {
        LiveRoomDTO dto = new LiveRoomDTO(1L, "直播1", "描述", 100L, "主播A",
                "cover.jpg", "rtmp://stream", null, null, 1000, 51, 1001L,
                "LIVE", FIXED_TIME, null, FIXED_TIME);
        LiveRoomVO vo = new LiveRoomVO(1L, "直播1", "主播A", "cover.jpg",
                "LIVE", null, 51, FIXED_TIME, null);

        given(liveRoomService.getRoom(1L)).willReturn(dto);
        given(liveConverter.dtoToVO(dto)).willReturn(vo);

        mockMvc.perform(post("/rooms/1/enter"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));

        verify(liveRoomService).incrementViewer(1L);
    }

    @Test
    @DisplayName("离开直播间 - 成功递减观众数")
    void leaveRoom_ShouldDecrementViewerAndReturnEnvelope() throws Exception {
        mockMvc.perform(post("/rooms/1/leave"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(liveRoomService).decrementViewer(1L);
    }
}
