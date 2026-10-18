package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.feign.CreateGroupActivityRequest;
import com.cloudmart.admin.dto.feign.CreateTieredPromotionRequest;
import com.cloudmart.admin.dto.feign.GroupActivityDTO;
import com.cloudmart.admin.dto.feign.TieredPromotionDTO;
import com.cloudmart.admin.feign.MarketingFeignClient;
import com.cloudmart.common.annotation.OperLog;
import com.cloudmart.common.annotation.RequiresPermission;
import com.cloudmart.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 业务管理-营销域：拼团活动、阶梯满减。
 * <p>路径前缀保持历史契约 {@code /business}，与拆分前 AdminBusinessController 完全一致。
 */
@RestController
@RequestMapping("/business")
@Tag(name = "业务管理", description = "管理后台对业务模块的代理接口：营销（拼团/满减）")
public class AdminMarketingController {

    private final MarketingFeignClient marketingFeignClient;

    public AdminMarketingController(MarketingFeignClient marketingFeignClient) {
        this.marketingFeignClient = marketingFeignClient;
    }

    // ==================== 拼团活动 ====================

    @GetMapping("/marketing/group/activities")
    @RequiresPermission("business:marketing:list")
    @Operation(summary = "拼团活动列表", description = "查询拼团活动列表")
    public ApiResponse<Object> listGroupActivities(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        return marketingFeignClient.listGroupActivities(status, page, pageSize);
    }

    @PostMapping("/marketing/group/activities")
    @OperLog(title = "拼团活动", businessType = 1)
    @RequiresPermission("business:marketing:add")
    @Operation(summary = "创建拼团活动", description = "创建新的拼团活动")
    public ApiResponse<GroupActivityDTO> createGroupActivity(@RequestBody CreateGroupActivityRequest request) {
        return marketingFeignClient.createGroupActivity(request);
    }

    @PutMapping("/marketing/group/activities/{id}")
    @OperLog(title = "拼团活动", businessType = 2)
    @RequiresPermission("business:marketing:edit")
    @Operation(summary = "更新拼团活动", description = "更新拼团活动信息")
    public ApiResponse<Object> updateGroupActivity(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return marketingFeignClient.updateGroupActivity(id, body);
    }

    @PutMapping("/marketing/group/activities/{id}/enable")
    @OperLog(title = "拼团活动", businessType = 2)
    @RequiresPermission("business:marketing:edit")
    @Operation(summary = "启用拼团活动", description = "启用拼团活动")
    public ApiResponse<GroupActivityDTO> enableGroupActivity(@PathVariable Long id) {
        return marketingFeignClient.enableGroupActivity(id);
    }

    @PutMapping("/marketing/group/activities/{id}/disable")
    @OperLog(title = "拼团活动", businessType = 2)
    @RequiresPermission("business:marketing:edit")
    @Operation(summary = "停用拼团活动", description = "停用拼团活动")
    public ApiResponse<GroupActivityDTO> disableGroupActivity(@PathVariable Long id) {
        return marketingFeignClient.disableGroupActivity(id);
    }

    @DeleteMapping("/marketing/group/activities/{id}")
    @OperLog(title = "拼团活动", businessType = 3)
    @RequiresPermission("business:marketing:remove")
    @Operation(summary = "删除拼团活动", description = "删除拼团活动")
    public ApiResponse<Void> deleteGroupActivity(@PathVariable Long id) {
        return marketingFeignClient.deleteGroupActivity(id);
    }

    @GetMapping("/marketing/group/orders")
    @RequiresPermission("business:marketing:list")
    @Operation(summary = "拼团组列表", description = "查询拼团组列表")
    public ApiResponse<Object> listGroupOrders(
            @RequestParam(value = "activityId", required = false) Long activityId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        return marketingFeignClient.listGroupOrders(activityId, status, page, pageSize);
    }

    // ==================== 阶梯满减 ====================

    @GetMapping("/marketing/tiered/promotions")
    @RequiresPermission("business:marketing:list")
    @Operation(summary = "满减活动列表", description = "查询阶梯满减活动列表")
    public ApiResponse<Object> listTieredPromotions(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        return marketingFeignClient.listTieredPromotions(status, page, pageSize);
    }

    @PostMapping("/marketing/tiered/promotions")
    @OperLog(title = "满减活动", businessType = 1)
    @RequiresPermission("business:marketing:add")
    @Operation(summary = "创建满减活动", description = "创建阶梯满减活动")
    public ApiResponse<TieredPromotionDTO> createTieredPromotion(@RequestBody CreateTieredPromotionRequest request) {
        return marketingFeignClient.createTieredPromotion(request);
    }

    @PutMapping("/marketing/tiered/promotions/{id}")
    @OperLog(title = "满减活动", businessType = 2)
    @RequiresPermission("business:marketing:edit")
    @Operation(summary = "更新满减活动", description = "更新阶梯满减活动信息")
    public ApiResponse<Object> updateTieredPromotion(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return marketingFeignClient.updateTieredPromotion(id, body);
    }

    @PutMapping("/marketing/tiered/promotions/{id}/enable")
    @OperLog(title = "满减活动", businessType = 2)
    @RequiresPermission("business:marketing:edit")
    @Operation(summary = "启用满减活动", description = "启用阶梯满减活动")
    public ApiResponse<TieredPromotionDTO> enableTieredPromotion(@PathVariable Long id) {
        return marketingFeignClient.enableTieredPromotion(id);
    }

    @PutMapping("/marketing/tiered/promotions/{id}/disable")
    @OperLog(title = "满减活动", businessType = 2)
    @RequiresPermission("business:marketing:edit")
    @Operation(summary = "停用满减活动", description = "停用阶梯满减活动")
    public ApiResponse<TieredPromotionDTO> disableTieredPromotion(@PathVariable Long id) {
        return marketingFeignClient.disableTieredPromotion(id);
    }

    @GetMapping("/marketing/tiered/promotions/{id}")
    @RequiresPermission("business:marketing:query")
    @Operation(summary = "满减活动详情", description = "查询阶梯满减活动详情")
    public ApiResponse<TieredPromotionDTO> getTieredPromotion(@PathVariable Long id) {
        return marketingFeignClient.getTieredPromotion(id);
    }

    @PostMapping("/marketing/tiered/promotions/{id}/preview")
    @RequiresPermission("business:marketing:query")
    @Operation(summary = "满减试算预览", description = "T10：发布前试算命中规则/门槛/优惠/原因；纯只读，不写订单、不预占券库存")
    public ApiResponse<Object> previewTieredPromotion(@PathVariable Long id,
                                                      @RequestBody Map<String, Object> body) {
        return marketingFeignClient.previewTieredPromotion(id, body);
    }

    @DeleteMapping("/marketing/tiered/promotions/{id}")
    @OperLog(title = "满减活动", businessType = 3)
    @RequiresPermission("business:marketing:remove")
    @Operation(summary = "删除满减活动", description = "删除阶梯满减活动")
    public ApiResponse<Void> deleteTieredPromotion(@PathVariable Long id) {
        return marketingFeignClient.deleteTieredPromotion(id);
    }
}
