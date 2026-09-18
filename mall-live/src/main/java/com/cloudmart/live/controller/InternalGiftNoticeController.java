package com.cloudmart.live.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.live.dto.GiftNoticeMessage;
import com.cloudmart.live.entity.LiveRoom;
import com.cloudmart.live.netty.DanmakuChannelHandler;
import com.cloudmart.live.repository.LiveRoomMapper;
import com.cloudmart.live.websocket.LiveDanmakuHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 内部-礼物支持接口（全站虚拟礼物）。
 *
 * <p>供 mall-wish 送礼链路使用：解析直播间归属（主播即收礼人）、
 * 送礼成功后的房间礼物特效广播（双栈：Spring WS + Netty 通道）。
 * 仅内部服务调用（X-Internal-Call 头认证）。</p>
 */
@Slf4j
@RestController
@RequestMapping("/internal/live")
@PreAuthorize("hasRole('INTERNAL')")
@RequiredArgsConstructor
@Tag(name = "内部-礼物支持", description = "直播间归属解析与礼物特效广播（mall-wish 送礼链路专用）")
public class InternalGiftNoticeController {

    private final LiveRoomMapper liveRoomMapper;
    private final LiveDanmakuHandler liveDanmakuHandler;
    private final DanmakuChannelHandler danmakuChannelHandler;

    @GetMapping("/rooms/{roomId}")
    @Operation(summary = "查询直播间归属", description = "返回 {roomId, ownerId(主播)}；房间不存在返回 data=null")
    public ApiResponse<Map<String, Object>> getRoomOwner(@PathVariable("roomId") Long roomId) {
        LiveRoom room = liveRoomMapper.selectOne(new LambdaQueryWrapper<LiveRoom>()
                .eq(LiveRoom::getId, roomId)
                .select(LiveRoom::getId, LiveRoom::getAnchorUserId)
                .last("LIMIT 1"));
        if (room == null) {
            return ApiResponse.ok(null);
        }
        return ApiResponse.ok(Map.of("roomId", room.getId(), "ownerId", room.getAnchorUserId()));
    }

    @PostMapping("/rooms/{roomId}/gift-notice")
    @Operation(summary = "直播间礼物特效广播", description = "送礼成功后触发；向房间内全部在线观众推送 GIFT 消息（双 WS 栈），无人在线静默成功")
    public ApiResponse<Void> broadcastGiftNotice(
            @PathVariable("roomId") Long roomId,
            @RequestBody Map<String, Object> body) {
        GiftNoticeMessage notice = new GiftNoticeMessage(
                GiftNoticeMessage.TYPE_GIFT,
                roomId,
                asLong(body.get("senderId")),
                (String) body.get("senderNickname"),
                asLong(body.get("receiverId")),
                asLong(body.get("giftId")),
                (String) body.get("giftName"),
                (String) body.get("giftIconUrl"),
                body.get("count") instanceof Number number ? number.intValue() : 1,
                (String) body.get("message"),
                System.currentTimeMillis());
        liveDanmakuHandler.broadcastGiftNotice(roomId, notice);
        danmakuChannelHandler.broadcastGiftNotice(roomId, notice);
        log.info("礼物特效广播: roomId={}, senderId={}, giftName={}, count={}",
                roomId, notice.senderId(), notice.giftName(), notice.count());
        return ApiResponse.ok(null);
    }

    private Long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }
}
