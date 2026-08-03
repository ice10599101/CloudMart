package com.cloudmart.live.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.live.converter.LiveConverter;
import com.cloudmart.live.dto.CreateLiveRoomRequest;
import com.cloudmart.live.dto.LiveRoomDTO;
import com.cloudmart.live.repository.LiveRoomMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminLiveRoomControllerTest {

    private MockMvc mockMvc;

    private final LiveRoomService liveRoomService = Mockito.mock(LiveRoomService.class);
    private final LiveConverter liveConverter = Mockito.mock(LiveConverter.class);
    private final LiveRoomMapper liveRoomMapper = Mockito.mock(LiveRoomMapper.class);

    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 5, 29, 10, 0);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminLiveRoomController(liveRoomService, liveConverter, liveRoomMapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("创建直播间 - 成功返回信封")
    void createRoom_ShouldReturnEnvelope() throws Exception {
        LiveRoomDTO dto = new LiveRoomDTO(1L, "新直播", "描述", 100L, "主播A",
                "cover.jpg", "rtmp://stream", null, null, 1000, 0, 0L,
                "OFFLINE", null, null, FIXED_TIME);
        LiveRoomVO vo = new LiveRoomVO(1L, "新直播", "主播A", "cover.jpg",
                "OFFLINE", null, 0, null, null);

        given(liveRoomService.createRoom(Mockito.any(CreateLiveRoomRequest.class))).willReturn(dto);
        given(liveConverter.dtoToVO(dto)).willReturn(vo);

        mockMvc.perform(post("/admin/live/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"新直播\",\"anchorUserId\":100,\"anchorName\":\"主播A\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.title").value("新直播"));
    }

    @Test
    @DisplayName("创建直播间 - 缺少必填字段返回校验错误")
    void createRoom_WhenMissingRequiredField_ShouldReturnValidationError() throws Exception {
        mockMvc.perform(post("/admin/live/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("开始直播 - 成功返回信封")
    void startLive_ShouldReturnEnvelope() throws Exception {
        LiveRoomDTO dto = new LiveRoomDTO(1L, "直播1", "描述", 100L, "主播A",
                "cover.jpg", "rtmp://stream", null, null, 1000, 0, 0L,
                "LIVE", FIXED_TIME, null, FIXED_TIME);
        LiveRoomVO vo = new LiveRoomVO(1L, "直播1", "主播A", "cover.jpg",
                "LIVE", null, 0, FIXED_TIME, null);

        given(liveRoomService.startLive(1L)).willReturn(dto);
        given(liveConverter.dtoToVO(dto)).willReturn(vo);

        mockMvc.perform(put("/admin/live/rooms/1/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("LIVE"));
    }

    @Test
    @DisplayName("结束直播 - 成功返回信封")
    void endLive_ShouldReturnEnvelope() throws Exception {
        LiveRoomDTO dto = new LiveRoomDTO(1L, "直播1", "描述", 100L, "主播A",
                "cover.jpg", "rtmp://stream", null, null, 1000, 0, 100L,
                "ENDED", FIXED_TIME, FIXED_TIME, FIXED_TIME);
        LiveRoomVO vo = new LiveRoomVO(1L, "直播1", "主播A", "cover.jpg",
                "ENDED", null, 0, FIXED_TIME, FIXED_TIME);

        given(liveRoomService.endLive(1L)).willReturn(dto);
        given(liveConverter.dtoToVO(dto)).willReturn(vo);

        mockMvc.perform(put("/admin/live/rooms/1/end"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ENDED"));
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

        mockMvc.perform(get("/admin/live/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    @DisplayName("获取直播间详情 - 成功返回信封")
    void getRoom_ShouldReturnEnvelope() throws Exception {
        LiveRoomDTO dto = new LiveRoomDTO(1L, "直播1", "描述", 100L, "主播A",
                "cover.jpg", "rtmp://stream", null, null, 1000, 50, 1000L,
                "LIVE", FIXED_TIME, null, FIXED_TIME);
        LiveRoomVO vo = new LiveRoomVO(1L, "直播1", "主播A", "cover.jpg",
                "LIVE", null, 50, FIXED_TIME, null);

        given(liveRoomService.getRoom(1L)).willReturn(dto);
        given(liveConverter.dtoToVO(dto)).willReturn(vo);

        mockMvc.perform(get("/admin/live/rooms/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
    }
}
