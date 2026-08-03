package com.cloudmart.live.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.live.dto.LiveRoomDTO;
import com.cloudmart.live.feign.SeckillFeignClient;
import com.cloudmart.live.service.LiveRoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 直播间专属秒杀接口。
 * 仅在直播间 LIVE 状态下允许参与秒杀，增强直播间互动性。
 */
@Tag(name = "直播间秒杀", description = "直播间专属秒杀抢购接口")
@RestController
@RequestMapping("/seckill")
public class LiveSeckillController {

    private final LiveRoomService liveRoomService;
    private final SeckillFeignClient seckillFeignClient;

    public LiveSeckillController(LiveRoomService liveRoomService,
                                  SeckillFeignClient seckillFeignClient) {
        this.liveRoomService = liveRoomService;
        this.seckillFeignClient = seckillFeignClient;
    }

    @Operation(summary = "直播间秒杀", description = "在直播间内参与专属秒杀活动，仅在直播间 LIVE 状态下可用")
    @PostMapping("/rooms/{roomId}/execute")
    public ApiResponse<Map<String, Object>> executeLiveSeckill(
            @Parameter(description = "用户ID") @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "直播间ID") @PathVariable Long roomId) {
        LiveRoomDTO room = liveRoomService.getRoom(roomId);
        if (!"LIVE".equals(room.status())) {
            throw new BusinessException("ROOM_NOT_LIVE", "直播间未在直播中，无法参与秒杀");
        }
        if (room.seckillActivityId() == null) {
            throw new BusinessException("NO_SECKILL_ACTIVITY", "该直播间暂无秒杀活动");
        }

        return seckillFeignClient.executeSeckill(userId, room.seckillActivityId());
    }

    @Operation(summary = "获取直播间秒杀活动信息", description = "查询直播间关联的秒杀活动详情")
    @GetMapping("/rooms/{roomId}/activity")
    public ApiResponse<Map<String, Object>> getLiveSeckillActivity(
            @Parameter(description = "直播间ID") @PathVariable Long roomId) {
        LiveRoomDTO room = liveRoomService.getRoom(roomId);
        if (room.seckillActivityId() == null) {
            throw new BusinessException("NO_SECKILL_ACTIVITY", "该直播间暂无秒杀活动");
        }

        return seckillFeignClient.getSeckillActivity(room.seckillActivityId(), "mall-live");
    }
}
