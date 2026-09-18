package com.cloudmart.wish.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * mall-live Feign 客户端（全站虚拟礼物）。
 *
 * <p>直播场景送礼使用：解析直播间主播（收礼人）+ 送礼成功后的房间礼物特效广播。
 * X-Internal-Call 头由 {@code FeignRequestInterceptor} 统一注入。
 * 广播为增强体验：live 不可用时送礼主链路不受影响（记 WARN 日志降级）。</p>
 */
@FeignClient(name = "mall-live", contextId = "wishLiveFeignClient", fallbackFactory = LiveFeignClientFallbackFactory.class)
public interface LiveFeignClient {

    /**
     * 查询直播间归属（主播用户 ID），用于解析收礼人。
     *
     * @return ApiResponse 包含 {roomId, ownerId}
     */
    @GetMapping("/internal/live/rooms/{roomId}")
    ApiResponse<Map<String, Object>> getRoomOwner(@PathVariable("roomId") Long roomId);

    /**
     * 直播间礼物特效广播（送礼成功后调用；失败不影响送礼结果）。
     *
     * @param body {senderId, senderNickname, receiverId, giftId, giftName, giftIconUrl, count, message}
     */
    @PostMapping("/internal/live/rooms/{roomId}/gift-notice")
    ApiResponse<Void> broadcastGiftNotice(@PathVariable("roomId") Long roomId,
                                          @RequestBody Map<String, Object> body);
}
