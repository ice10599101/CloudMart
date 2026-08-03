package com.cloudmart.admin.feign;

import com.cloudmart.admin.dto.feign.CouponSearchRequest;
import com.cloudmart.admin.dto.feign.CouponTemplateDTO;
import com.cloudmart.admin.dto.feign.CreateCouponTemplateRequest;
import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(contextId = "couponFeignClient", name = "mall-coupon", path = "/admin/coupon-templates", fallbackFactory = CouponFeignClientFallbackFactory.class)
public interface CouponFeignClient {

    @GetMapping
    ApiResponse<List<CouponTemplateDTO>> listTemplates(@SpringQueryMap CouponSearchRequest request);

    @GetMapping("/{id}")
    ApiResponse<CouponTemplateDTO> getTemplateById(@PathVariable("id") Long id);

    @PostMapping
    ApiResponse<CouponTemplateDTO> createTemplate(@RequestBody CreateCouponTemplateRequest request);

    @PutMapping("/{id}")
    ApiResponse<Object> updateTemplate(@PathVariable("id") Long id, @RequestBody Map<String, Object> body);

    @PutMapping("/{id}/disable")
    ApiResponse<CouponTemplateDTO> disableTemplate(@PathVariable("id") Long id);

    @PutMapping("/{id}/enable")
    ApiResponse<CouponTemplateDTO> enableTemplate(@PathVariable("id") Long id);

    @DeleteMapping("/{id}")
    ApiResponse<Void> deleteCoupon(@PathVariable("id") Long id);
}
