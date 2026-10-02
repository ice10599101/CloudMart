package com.cloudmart.admin.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * mall-seckill 秒杀请求运营查询客户端（T09）：排队中/终态失败请求对运营可见
 * ——恢复对账与释放动作全部留痕于请求行（fail_reason/send_attempts）。
 */
@FeignClient(contextId = "seckillRequestFeignClient", name = "mall-seckill",
        fallbackFactory = SeckillRequestFeignClientFallbackFactory.class)
public interface SeckillRequestFeignClient {

    @GetMapping("/admin/seckill/requests")
    ApiResponse<Map<String, Object>> pageRequests(
            @RequestParam("page") long page,
            @RequestParam("pageSize") long pageSize,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "activityId", required = false) Long activityId,
            @RequestParam(value = "userId", required = false) Long userId);
}
