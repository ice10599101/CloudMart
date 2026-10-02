package com.cloudmart.admin.feign;

import com.cloudmart.admin.dto.feign.*;
import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(contextId = "marketingFeignClient", name = "mall-marketing", path = "/admin/marketing", fallbackFactory = MarketingFeignClientFallbackFactory.class)
public interface MarketingFeignClient {

    // ==================== 拼团活动 ====================

    @GetMapping("/admin/marketing/group/activities")
    ApiResponse<Object> listGroupActivities(@RequestParam(value = "status", required = false) String status,
                                            @RequestParam("page") Integer page,
                                            @RequestParam("size") Integer size);

    @PostMapping("/admin/marketing/group/activities")
    ApiResponse<GroupActivityDTO> createGroupActivity(@RequestBody CreateGroupActivityRequest request);

    @PutMapping("/admin/marketing/group/activities/{id}")
    ApiResponse<Object> updateGroupActivity(@PathVariable("id") Long id, @RequestBody Map<String, Object> body);

    @PutMapping("/admin/marketing/group/activities/{id}/enable")
    ApiResponse<GroupActivityDTO> enableGroupActivity(@PathVariable("id") Long id);

    @PutMapping("/admin/marketing/group/activities/{id}/disable")
    ApiResponse<GroupActivityDTO> disableGroupActivity(@PathVariable("id") Long id);

    @DeleteMapping("/admin/marketing/group/activities/{id}")
    ApiResponse<Void> deleteGroupActivity(@PathVariable("id") Long id);

    @GetMapping("/admin/marketing/group/orders")
    ApiResponse<Object> listGroupOrders(@RequestParam(value = "activityId", required = false) Long activityId,
                                        @RequestParam(value = "status", required = false) String status,
                                        @RequestParam("page") Integer page,
                                        @RequestParam("size") Integer size);

    // ==================== 阶梯满减 ====================

    @GetMapping("/admin/marketing/tiered/promotions")
    ApiResponse<Object> listTieredPromotions(@RequestParam(value = "status", required = false) String status,
                                             @RequestParam("page") Integer page,
                                             @RequestParam("size") Integer size);

    @PostMapping("/admin/marketing/tiered/promotions")
    ApiResponse<TieredPromotionDTO> createTieredPromotion(@RequestBody CreateTieredPromotionRequest request);

    @PutMapping("/admin/marketing/tiered/promotions/{id}")
    ApiResponse<Object> updateTieredPromotion(@PathVariable("id") Long id, @RequestBody Map<String, Object> body);

    @PutMapping("/admin/marketing/tiered/promotions/{id}/enable")
    ApiResponse<TieredPromotionDTO> enableTieredPromotion(@PathVariable("id") Long id);

    @PutMapping("/admin/marketing/tiered/promotions/{id}/disable")
    ApiResponse<TieredPromotionDTO> disableTieredPromotion(@PathVariable("id") Long id);

    @GetMapping("/admin/marketing/tiered/promotions/{id}")
    ApiResponse<TieredPromotionDTO> getTieredPromotion(@PathVariable("id") Long id);

    @DeleteMapping("/admin/marketing/tiered/promotions/{id}")
    ApiResponse<Void> deleteTieredPromotion(@PathVariable("id") Long id);
}
