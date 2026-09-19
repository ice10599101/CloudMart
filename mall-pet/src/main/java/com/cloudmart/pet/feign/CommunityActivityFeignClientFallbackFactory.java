package com.cloudmart.pet.feign;

import com.cloudmart.common.api.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * mall-wish 活动客户端降级工厂（Fail-Open）：活动提醒是增值提醒，
 * 降级返回空列表 → 本次评估跳过活动提醒，宠物页主流程不受影响。
 */
@Slf4j
@Component
public class CommunityActivityFeignClientFallbackFactory implements FallbackFactory<CommunityActivityFeignClient> {

    @Override
    public CommunityActivityFeignClient create(Throwable cause) {
        log.warn("mall-wish 活动客户端降级: {}", cause.getMessage());
        return new CommunityActivityFeignClient() {
            @Override
            public ApiResponse<List<CommunityActivityFeignClient.CommunityActivityVO>> listActivities() {
                return ApiResponse.ok(List.of());
            }
        };
    }
}
