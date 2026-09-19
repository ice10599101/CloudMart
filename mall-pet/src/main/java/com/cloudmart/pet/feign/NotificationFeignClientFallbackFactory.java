package com.cloudmart.pet.feign;

import com.cloudmart.common.api.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * mall-notification 降级工厂（Fail-Open）：提醒列表是只读展示型数据，
 * 降级返回空列表 + 未读数 0，宠物页主流程不受影响。
 */
@Slf4j
@Component
public class NotificationFeignClientFallbackFactory implements FallbackFactory<NotificationFeignClient> {

    @Override
    public NotificationFeignClient create(Throwable cause) {
        log.warn("mall-notification Feign 调用降级: {}", cause.getMessage());
        return new NotificationFeignClient() {
            @Override
            public ApiResponse<List<NotificationFeignClient.NotificationItemVO>> listNotifications(
                    Long userId, String type, Integer page, Integer pageSize) {
                return ApiResponse.ok(List.of());
            }

            @Override
            public ApiResponse<Long> getUnreadCount(Long userId, String type) {
                return ApiResponse.ok(0L);
            }

            @Override
            public ApiResponse<Long> getUnreadChatCount(Long userId) {
                return ApiResponse.ok(0L);
            }
        };
    }
}
