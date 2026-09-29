package com.cloudmart.admin.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * mall-coupon 兑换码管理 Feign 客户端。
 *
 * <p>下游端点 {@code /admin/exchange-codes/**} 由
 * {@code @PreAuthorize("hasRole('INTERNAL')")} 保护；出站服务令牌由
 * {@code ServiceTokenFeignInterceptor} 按 outbound-scopes（mall-coupon=coupon:admin）
 * 自动签发。</p>
 */
@FeignClient(contextId = "exchangeCodeFeignClient", name = "mall-coupon",
        path = "/admin/exchange-codes",
        fallbackFactory = ExchangeCodeFeignClientFallbackFactory.class)
public interface ExchangeCodeFeignClient {

    @PostMapping("/generate")
    ApiResponse<Object> generateBatch(@RequestBody Map<String, Object> body);

    @GetMapping("/{code}")
    ApiResponse<Object> getExchangeCode(@PathVariable("code") String code);

    @GetMapping
    ApiResponse<Object> listExchangeCodes(@RequestParam("templateId") Long templateId,
                                          @RequestParam(value = "status", required = false) String status,
                                          @RequestParam("page") int page,
                                          @RequestParam("pageSize") int pageSize);

    @PutMapping("/{code}/disable")
    ApiResponse<Void> disableExchangeCode(@PathVariable("code") String code);
}
