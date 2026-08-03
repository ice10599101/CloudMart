package com.cloudmart.live.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.live.converter.LiveConverter;
import com.cloudmart.live.dto.CreateLiveRoomRequest;
import com.cloudmart.live.dto.LiveRoomDTO;
import com.cloudmart.live.entity.LiveRoom;
import com.cloudmart.live.repository.LiveRoomMapper;
import com.cloudmart.live.service.LiveRoomService;
import com.cloudmart.live.vo.LiveRoomVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@Tag(name = "直播间管理(后台)", description = "管理后台直播间管理接口，仅供内部服务调用")
@RestController
@RequestMapping("/admin/live/rooms")
public class AdminLiveRoomController {

    private final LiveRoomService liveRoomService;
    private final LiveConverter liveConverter;
    private final LiveRoomMapper roomMapper;

    public AdminLiveRoomController(LiveRoomService liveRoomService, LiveConverter liveConverter, LiveRoomMapper roomMapper) {
        this.liveRoomService = liveRoomService;
        this.liveConverter = liveConverter;
        this.roomMapper = roomMapper;
    }

    @Operation(summary = "创建直播间")
    @PostMapping
    public ApiResponse<LiveRoomVO> createRoom(@Valid @RequestBody CreateLiveRoomRequest request) {
        LiveRoomDTO dto = liveRoomService.createRoom(request);
        return ApiResponse.ok(liveConverter.dtoToVO(dto));
    }

    @Operation(summary = "开始直播")
    @PutMapping("/{roomId}/start")
    public ApiResponse<LiveRoomVO> startLive(
            @Parameter(description = "直播间ID") @PathVariable("roomId") Long roomId) {
        LiveRoomDTO dto = liveRoomService.startLive(roomId);
        return ApiResponse.ok(liveConverter.dtoToVO(dto));
    }

    @Operation(summary = "结束直播")
    @PutMapping("/{roomId}/end")
    public ApiResponse<LiveRoomVO> endLive(
            @Parameter(description = "直播间ID") @PathVariable("roomId") Long roomId) {
        LiveRoomDTO dto = liveRoomService.endLive(roomId);
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

    @Operation(summary = "获取直播间详情")
    @GetMapping("/{roomId}")
    public ApiResponse<LiveRoomVO> getRoom(
            @Parameter(description = "直播间ID") @PathVariable("roomId") Long roomId) {
        LiveRoomDTO dto = liveRoomService.getRoom(roomId);
        return ApiResponse.ok(liveConverter.dtoToVO(dto));
    }

    @Operation(summary = "更新直播间")
    @PutMapping("/{roomId}")
    public ApiResponse<LiveRoomVO> updateRoom(
            @Parameter(description = "直播间ID") @PathVariable("roomId") Long roomId,
            @RequestBody Map<String, Object> body) {
        LiveRoom entity = roomMapper.selectById(roomId);
        if (entity == null) {
            throw new BusinessException("ROOM_NOT_FOUND", "直播间不存在");
        }
        if (body.containsKey("title")) {
            entity.setTitle((String) body.get("title"));
        }
        if (body.containsKey("description")) {
            entity.setDescription((String) body.get("description"));
        }
        if (body.containsKey("anchorUserId")) {
            entity.setAnchorUserId(((Number) body.get("anchorUserId")).longValue());
        }
        if (body.containsKey("anchorName")) {
            entity.setAnchorName((String) body.get("anchorName"));
        }
        if (body.containsKey("coverImage")) {
            entity.setCoverImage((String) body.get("coverImage"));
        }
        if (body.containsKey("streamUrl")) {
            entity.setStreamUrl((String) body.get("streamUrl"));
        }
        if (body.containsKey("productId")) {
            entity.setProductId(((Number) body.get("productId")).longValue());
        }
        if (body.containsKey("seckillActivityId")) {
            entity.setSeckillActivityId(((Number) body.get("seckillActivityId")).longValue());
        }
        if (body.containsKey("maxViewers")) {
            entity.setMaxViewers(((Number) body.get("maxViewers")).intValue());
        }
        if (body.containsKey("status")) {
            entity.setStatus((String) body.get("status"));
        }
        roomMapper.updateById(entity);
        return ApiResponse.ok(liveConverter.toVO(entity));
    }

    @Operation(summary = "删除直播间")
    @DeleteMapping("/{roomId}")
    public ApiResponse<Void> deleteRoom(
            @Parameter(description = "直播间ID") @PathVariable("roomId") Long roomId) {
        LiveRoom entity = roomMapper.selectById(roomId);
        if (entity == null) {
            throw new BusinessException("ROOM_NOT_FOUND", "直播间不存在");
        }
        roomMapper.deleteById(roomId);
        return ApiResponse.ok(null);
    }
}
