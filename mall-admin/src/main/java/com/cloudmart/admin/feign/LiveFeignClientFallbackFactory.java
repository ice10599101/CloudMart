package com.cloudmart.admin.feign;

import com.cloudmart.admin.dto.feign.CreateLiveRoomRequest;
import com.cloudmart.admin.dto.feign.LiveRoomDTO;
import com.cloudmart.admin.dto.feign.LiveRoomSearchRequest;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class LiveFeignClientFallbackFactory implements FallbackFactory<LiveFeignClient> {

    @Override
    public LiveFeignClient create(Throwable cause) {
        log.error("直播服务调用失败: {}", cause.getMessage());
        return new LiveFeignClient() {
            @Override
            public ApiResponse<Object> listRooms(LiveRoomSearchRequest request) {
                throw new BusinessException("LIVE_SERVICE_UNAVAILABLE", "直播服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<LiveRoomDTO> createRoom(CreateLiveRoomRequest request) {
                throw new BusinessException("LIVE_SERVICE_UNAVAILABLE", "直播服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Object> updateRoom(Long roomId, Map<String, Object> body) {
                throw new BusinessException("LIVE_SERVICE_UNAVAILABLE", "直播服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<LiveRoomDTO> startLive(Long roomId) {
                throw new BusinessException("LIVE_SERVICE_UNAVAILABLE", "直播服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<LiveRoomDTO> endLive(Long roomId) {
                throw new BusinessException("LIVE_SERVICE_UNAVAILABLE", "直播服务不可用，请稍后重试");
            }

            @Override
            public ApiResponse<Void> deleteRoom(Long roomId) {
                throw new BusinessException("LIVE_SERVICE_UNAVAILABLE", "直播服务不可用，请稍后重试");
            }
        };
    }
}
