package com.cloudmart.admin.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@FeignClient(contextId = "riskFeignClient", name = "mall-risk",
        fallbackFactory = RiskFeignClientFallbackFactory.class)
public interface RiskFeignClient {

    @PostMapping("/blacklist")
    ApiResponse<Object> addToBlacklist(@RequestParam("type") String targetType,
                                       @RequestParam("value") String targetValue,
                                       @RequestParam("reason") String reason,
                                       @RequestParam(value = "expiredAt", required = false) LocalDateTime expiredAt);

    @DeleteMapping("/blacklist/{type}/{value}")
    ApiResponse<Void> removeFromBlacklist(@PathVariable("type") String targetType,
                                           @PathVariable("value") String targetValue);

    @GetMapping("/blacklist/check")
    ApiResponse<Boolean> checkBlacklist(@RequestParam("type") String targetType,
                                         @RequestParam("value") String targetValue);

    @GetMapping("/blacklist/list")
    ApiResponse<Object> listBlacklist(@SpringQueryMap Map<String, Object> params);

    @GetMapping("/records")
    ApiResponse<Object> listRiskRecords(@SpringQueryMap Map<String, Object> params);

    @GetMapping("/records/{id}")
    ApiResponse<Object> getRiskRecord(@PathVariable("id") Long id);

    @GetMapping("/rules")
    ApiResponse<Object> listRiskRules(@SpringQueryMap Map<String, Object> params);

    @PostMapping("/rules")
    ApiResponse<Object> createRiskRule(@RequestBody Map<String, Object> body);

    @PutMapping("/rules/{id}")
    ApiResponse<Object> updateRiskRule(@PathVariable("id") Long id, @RequestBody Map<String, Object> body);

    @DeleteMapping("/rules/{id}")
    ApiResponse<Void> deleteRiskRule(@PathVariable("id") Long id);
}
