package com.cloudmart.live.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.live.converter.LiveConverter;
import com.cloudmart.live.dto.LiveRoomDTO;
import com.cloudmart.live.service.LiveRoomService;
import com.cloudmart.live.vo.LiveRoomVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(name = "直播间管理", description = "直播间查询和操作接口")
@RestController
@RequestMapping("/rooms")
public class LiveRoomController {

    private final LiveRoomService liveRoomService;
    private final LiveConverter liveConverter;

    public LiveRoomController(LiveRoomService liveRoomService, LiveConverter liveConverter) {
        this.liveRoomService = liveRoomService;
        this.liveConverter = liveConverter;
    }

    @Operation(summary = "获取直播间详情")
    @GetMapping("/{roomId}")
    public ApiResponse<LiveRoomVO> getRoom(
            @Parameter(description = "直播间ID") @PathVariable("roomId") Long roomId) {
        LiveRoomDTO dto = liveRoomService.getRoom(roomId);
        return ApiResponse.ok(liveConverter.dtoToVO(dto));
    }

    @Operation(summary = "查询直播间列表")
    @GetMapping
    public ApiResponse<IPage<LiveRoomVO>> listRooms(
            @Parameter(description = "状态筛选") @RequestParam(required = false) String status,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int size) {
        IPage<LiveRoomDTO> dtoPage = liveRoomService.listRooms(status, page, size);
        IPage<LiveRoomVO> voPage = dtoPage.convert(liveConverter::dtoToVO);
        return ApiResponse.ok(voPage);
    }

    @Operation(summary = "进入直播间")
    @PostMapping("/{roomId}/enter")
    public ApiResponse<LiveRoomVO> enterRoom(
            @Parameter(description = "直播间ID") @PathVariable("roomId") Long roomId) {
        liveRoomService.incrementViewer(roomId);
        LiveRoomDTO dto = liveRoomService.getRoom(roomId);
        return ApiResponse.ok(liveConverter.dtoToVO(dto));
    }

    @Operation(summary = "离开直播间")
    @PostMapping("/{roomId}/leave")
    public ApiResponse<Void> leaveRoom(
            @Parameter(description = "直播间ID") @PathVariable("roomId") Long roomId) {
        liveRoomService.decrementViewer(roomId);
        return ApiResponse.ok(null);
    }
}
