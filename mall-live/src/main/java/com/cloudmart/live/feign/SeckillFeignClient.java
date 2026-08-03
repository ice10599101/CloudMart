package com.cloudmart.live.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 秒杀服务远程调用客户端，用于直播间专属秒杀。
 */
@FeignClient(name = "mall-seckill", contextId = "liveSeckillClient", fallbackFactory = SeckillFeignClientFallbackFactory.class)
public interface SeckillFeignClient {

    @PostMapping("/execute")
    ApiResponse<Map<String, Object>> executeSeckill(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam("activityId") Long activityId
    );

    @GetMapping("/activities/{activityId}")
    ApiResponse<Map<String, Object>> getSeckillActivity(
            @PathVariable("activityId") Long activityId,
            @RequestHeader("X-Internal-Call") String internalCall
    );
}
