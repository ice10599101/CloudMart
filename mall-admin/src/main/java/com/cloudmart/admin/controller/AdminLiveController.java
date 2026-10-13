package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.feign.CreateLiveRoomRequest;
import com.cloudmart.admin.dto.feign.LiveRoomDTO;
import com.cloudmart.admin.feign.LiveFeignClient;
import com.cloudmart.common.annotation.OperLog;
import com.cloudmart.common.annotation.RequiresPermission;
import com.cloudmart.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 业务管理-直播域：直播间 CRUD 与开播/关播。
 * <p>路径前缀保持历史契约 {@code /business}，与拆分前 AdminBusinessController 完全一致。
 */
@RestController
@RequestMapping("/business")
@Tag(name = "业务管理", description = "管理后台对业务模块的代理接口：直播管理")
public class AdminLiveController {

    private final LiveFeignClient liveFeignClient;

    public AdminLiveController(LiveFeignClient liveFeignClient) {
        this.liveFeignClient = liveFeignClient;
    }

    // ==================== 直播管理 ====================

    @GetMapping("/live/rooms")
    @RequiresPermission("business:live:list")
    @Operation(summary = "直播间列表", description = "查询直播间列表")
    public ApiResponse<Object> listLiveRooms(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        return liveFeignClient.listRooms(status, page, pageSize);
    }

    @PostMapping("/live/rooms")
    @OperLog(title = "直播管理", businessType = 1)
    @RequiresPermission("business:live:add")
    @Operation(summary = "创建直播间", description = "创建新的直播间")
    public ApiResponse<LiveRoomDTO> createLiveRoom(@RequestBody CreateLiveRoomRequest request) {
        return liveFeignClient.createRoom(request);
    }

    @PutMapping("/live/rooms/{roomId}")
    @OperLog(title = "直播管理", businessType = 2)
    @RequiresPermission("business:live:edit")
    @Operation(summary = "更新直播间", description = "更新直播间信息")
    public ApiResponse<Object> updateLiveRoom(@PathVariable Long roomId, @RequestBody Map<String, Object> body) {
        return liveFeignClient.updateRoom(roomId, body);
    }

    @PutMapping("/live/rooms/{roomId}/start")
    @OperLog(title = "直播管理", businessType = 2)
    @RequiresPermission("business:live:edit")
    @Operation(summary = "开播", description = "开始直播")
    public ApiResponse<LiveRoomDTO> startLive(@PathVariable Long roomId) {
        return liveFeignClient.startLive(roomId);
    }

    @PutMapping("/live/rooms/{roomId}/end")
    @OperLog(title = "直播管理", businessType = 2)
    @RequiresPermission("business:live:edit")
    @Operation(summary = "结束直播", description = "结束直播")
    public ApiResponse<LiveRoomDTO> endLive(@PathVariable Long roomId) {
        return liveFeignClient.endLive(roomId);
    }

    @DeleteMapping("/live/rooms/{id}")
    @OperLog(title = "直播管理", businessType = 3)
    @RequiresPermission("business:live:remove")
    @Operation(summary = "删除直播间", description = "删除直播间")
    public ApiResponse<Void> deleteLiveRoom(@PathVariable Long id) {
        return liveFeignClient.deleteRoom(id);
    }
}
