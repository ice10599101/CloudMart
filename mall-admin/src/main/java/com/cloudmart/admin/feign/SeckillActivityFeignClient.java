package com.cloudmart.admin.feign;

import com.cloudmart.admin.dto.feign.CreateActivityRequest;
import com.cloudmart.admin.dto.feign.SeckillActivityDTO;
import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(contextId = "seckillActivityFeignClient", name = "mall-seckill", path = "/admin/seckill/activities", fallbackFactory = SeckillActivityFeignClientFallbackFactory.class)
public interface SeckillActivityFeignClient {

    @GetMapping
    ApiResponse<List<SeckillActivityDTO>> listActivities(@RequestParam(value = "status", required = false) String status);

    @GetMapping("/{activityId}")
    ApiResponse<SeckillActivityDTO> getActivity(@PathVariable("activityId") Long activityId);

    @PostMapping
    ApiResponse<SeckillActivityDTO> createActivity(@RequestBody CreateActivityRequest request);

    @PutMapping("/{activityId}")
    ApiResponse<Object> updateActivity(@PathVariable("activityId") Long activityId, @RequestBody Map<String, Object> body);

    @PutMapping("/{activityId}/status")
    ApiResponse<SeckillActivityDTO> updateActivityStatus(
            @PathVariable("activityId") Long activityId, @RequestParam String status);

    @DeleteMapping("/{activityId}")
    ApiResponse<Void> deleteActivity(@PathVariable("activityId") Long activityId);
}
