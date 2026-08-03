package com.cloudmart.admin.feign;

import com.cloudmart.admin.dto.feign.*;
import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(contextId = "marketingFeignClient", name = "mall-marketing", path = "/admin/marketing", fallbackFactory = MarketingFeignClientFallbackFactory.class)
public interface MarketingFeignClient {

    // ==================== 拼团活动 ====================

    @GetMapping("/group/activities")
    ApiResponse<Object> listGroupActivities(@SpringQueryMap GroupActivitySearchRequest request);

    @PostMapping("/group/activities")
    ApiResponse<GroupActivityDTO> createGroupActivity(@RequestBody CreateGroupActivityRequest request);

    @PutMapping("/group/activities/{id}")
    ApiResponse<Object> updateGroupActivity(@PathVariable("id") Long id, @RequestBody Map<String, Object> body);

    @PutMapping("/group/activities/{id}/enable")
    ApiResponse<GroupActivityDTO> enableGroupActivity(@PathVariable("id") Long id);

    @PutMapping("/group/activities/{id}/disable")
    ApiResponse<GroupActivityDTO> disableGroupActivity(@PathVariable("id") Long id);

    @DeleteMapping("/group/activities/{id}")
    ApiResponse<Void> deleteGroupActivity(@PathVariable("id") Long id);

    @GetMapping("/group/orders")
    ApiResponse<Object> listGroupOrders(@SpringQueryMap GroupOrderSearchRequest request);

    // ==================== 阶梯满减 ====================

    @GetMapping("/tiered/promotions")
    ApiResponse<Object> listTieredPromotions(@SpringQueryMap TieredPromotionSearchRequest request);

    @PostMapping("/tiered/promotions")
    ApiResponse<TieredPromotionDTO> createTieredPromotion(@RequestBody CreateTieredPromotionRequest request);

    @PutMapping("/tiered/promotions/{id}")
    ApiResponse<Object> updateTieredPromotion(@PathVariable("id") Long id, @RequestBody Map<String, Object> body);

    @PutMapping("/tiered/promotions/{id}/enable")
    ApiResponse<TieredPromotionDTO> enableTieredPromotion(@PathVariable("id") Long id);

    @PutMapping("/tiered/promotions/{id}/disable")
    ApiResponse<TieredPromotionDTO> disableTieredPromotion(@PathVariable("id") Long id);

    @GetMapping("/tiered/promotions/{id}")
    ApiResponse<TieredPromotionDTO> getTieredPromotion(@PathVariable("id") Long id);

    @DeleteMapping("/tiered/promotions/{id}")
    ApiResponse<Void> deleteTieredPromotion(@PathVariable("id") Long id);
}
