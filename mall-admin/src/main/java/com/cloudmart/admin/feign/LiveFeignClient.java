package com.cloudmart.admin.feign;

import com.cloudmart.admin.dto.feign.CreateLiveRoomRequest;
import com.cloudmart.admin.dto.feign.LiveRoomDTO;
import com.cloudmart.admin.dto.feign.LiveRoomSearchRequest;
import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(contextId = "liveFeignClient", name = "mall-live", path = "/admin/live", fallbackFactory = LiveFeignClientFallbackFactory.class)
public interface LiveFeignClient {

    @GetMapping("/rooms")
    ApiResponse<Object> listRooms(@SpringQueryMap LiveRoomSearchRequest request);

    @PostMapping("/rooms")
    ApiResponse<LiveRoomDTO> createRoom(@RequestBody CreateLiveRoomRequest request);

    @PutMapping("/rooms/{roomId}")
    ApiResponse<Object> updateRoom(@PathVariable("roomId") Long roomId, @RequestBody Map<String, Object> body);

    @PutMapping("/rooms/{roomId}/start")
    ApiResponse<LiveRoomDTO> startLive(@PathVariable("roomId") Long roomId);

    @PutMapping("/rooms/{roomId}/end")
    ApiResponse<LiveRoomDTO> endLive(@PathVariable("roomId") Long roomId);

    @DeleteMapping("/rooms/{roomId}")
    ApiResponse<Void> deleteRoom(@PathVariable("roomId") Long roomId);
}
