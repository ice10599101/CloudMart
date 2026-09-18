package com.cloudmart.wish.feign;

import com.cloudmart.common.api.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * mall-live Feign 降级工厂（全站虚拟礼物）。
 *
 * <p>直播服务不可用时的降级策略：房间归属解析失败 → 送礼直接失败
 * （收礼人不明不可扣费）；礼物特效广播失败 → 仅记日志，送礼结果不受影响。</p>
 */
@Slf4j
@Component
public class LiveFeignClientFallbackFactory implements FallbackFactory<LiveFeignClient> {

    @Override
    public LiveFeignClient create(Throwable cause) {
        log.warn("mall-live Feign 降级: {}", cause.getMessage());
        return new LiveFeignClient() {

            @Override
            public ApiResponse<Map<String, Object>> getRoomOwner(Long roomId) {
                return ApiResponse.fail("LIVE_SERVICE_UNAVAILABLE", "直播服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> broadcastGiftNotice(Long roomId, Map<String, Object> body) {
                log.warn("直播间礼物广播失败(降级), roomId={}", roomId);
                return ApiResponse.fail("LIVE_SERVICE_UNAVAILABLE", "直播服务不可用");
            }
        };
    }
}
