package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.feign.AddSeckillProductRequest;
import com.cloudmart.admin.dto.feign.CreateActivityRequest;
import com.cloudmart.admin.dto.feign.SeckillActivityDTO;
import com.cloudmart.admin.dto.feign.SeckillProductDTO;
import com.cloudmart.admin.feign.SeckillActivityFeignClient;
import com.cloudmart.admin.feign.SeckillProductFeignClient;
import com.cloudmart.admin.feign.SeckillRequestFeignClient;
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

import java.util.List;
import java.util.Map;

/**
 * 业务管理-秒杀域：秒杀活动、秒杀请求事实（T09）、活动商品。
 * <p>路径前缀保持历史契约 {@code /business}，与拆分前 AdminBusinessController 完全一致。
 */
@RestController
@RequestMapping("/business")
@Tag(name = "业务管理", description = "管理后台对业务模块的代理接口：秒杀活动/请求/商品")
public class AdminSeckillController {

    private final SeckillActivityFeignClient seckillActivityFeignClient;
    private final SeckillRequestFeignClient seckillRequestFeignClient;
    private final SeckillProductFeignClient seckillProductFeignClient;

    public AdminSeckillController(SeckillActivityFeignClient seckillActivityFeignClient,
                                  SeckillRequestFeignClient seckillRequestFeignClient,
                                  SeckillProductFeignClient seckillProductFeignClient) {
        this.seckillActivityFeignClient = seckillActivityFeignClient;
        this.seckillRequestFeignClient = seckillRequestFeignClient;
        this.seckillProductFeignClient = seckillProductFeignClient;
    }

    // ==================== 秒杀活动 ====================

    @GetMapping("/seckill/activities")
    @RequiresPermission("business:seckill:list")
    @Operation(summary = "秒杀活动列表", description = "查询秒杀活动列表")
    public ApiResponse<List<SeckillActivityDTO>> listSeckillActivities(
            @RequestParam(value = "status", required = false) String status) {
        return seckillActivityFeignClient.listActivities(status);
    }

    @GetMapping("/seckill/activities/{activityId}")
    @RequiresPermission("business:seckill:query")
    @Operation(summary = "秒杀活动详情", description = "查询秒杀活动详情")
    public ApiResponse<SeckillActivityDTO> getSeckillActivity(@PathVariable Long activityId) {
        return seckillActivityFeignClient.getActivity(activityId);
    }

    @PostMapping("/seckill/activities")
    @OperLog(title = "秒杀活动", businessType = 1)
    @RequiresPermission("business:seckill:add")
    @Operation(summary = "创建秒杀活动", description = "创建新的秒杀活动")
    public ApiResponse<SeckillActivityDTO> createSeckillActivity(@RequestBody CreateActivityRequest request) {
        return seckillActivityFeignClient.createActivity(request);
    }

    @PutMapping("/seckill/activities/{activityId}")
    @OperLog(title = "秒杀活动", businessType = 2)
    @RequiresPermission("business:seckill:edit")
    @Operation(summary = "更新秒杀活动", description = "更新秒杀活动信息")
    public ApiResponse<Object> updateSeckillActivity(@PathVariable Long activityId, @RequestBody Map<String, Object> body) {
        return seckillActivityFeignClient.updateActivity(activityId, body);
    }

    @PutMapping("/seckill/activities/{activityId}/status")
    @OperLog(title = "秒杀活动", businessType = 2)
    @RequiresPermission("business:seckill:edit")
    @Operation(summary = "更新活动状态", description = "手动更新秒杀活动状态")
    public ApiResponse<SeckillActivityDTO> updateSeckillActivityStatus(
            @PathVariable Long activityId, @RequestParam String status) {
        return seckillActivityFeignClient.updateActivityStatus(activityId, status);
    }

    @DeleteMapping("/seckill/activities/{id}")
    @OperLog(title = "秒杀活动", businessType = 3)
    @RequiresPermission("business:seckill:remove")
    @Operation(summary = "删除秒杀活动", description = "删除秒杀活动")
    public ApiResponse<Void> deleteSeckillActivity(@PathVariable Long id) {
        return seckillActivityFeignClient.deleteActivity(id);
    }

    @GetMapping("/seckill/requests")
    @RequiresPermission("business:seckill:list")
    @Operation(summary = "秒杀请求运营查询", description = "T09：按状态/活动/用户筛选秒杀请求事实；"
            + "PENDING 停留过久或 FAILED 集中出现需关注（消息投递或结果回写异常）")
    public ApiResponse<Map<String, Object>> listSeckillRequests(
            @RequestParam(value = "page", defaultValue = "1") long page,
            @RequestParam(value = "pageSize", defaultValue = "20") long pageSize,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "activityId", required = false) Long activityId,
            @RequestParam(value = "userId", required = false) Long userId) {
        return seckillRequestFeignClient.pageRequests(page, pageSize, status, activityId, userId);
    }

    // ==================== 秒杀商品 ====================

    @GetMapping("/seckill/products/activity/{activityId}")
    @RequiresPermission("business:seckill:list")
    @Operation(summary = "活动秒杀商品", description = "查询指定活动下的秒杀商品")
    public ApiResponse<List<SeckillProductDTO>> listSeckillProducts(@PathVariable Long activityId) {
        return seckillProductFeignClient.listProductsByActivity(activityId);
    }

    @GetMapping("/seckill/products/{productId}")
    @RequiresPermission("business:seckill:query")
    @Operation(summary = "秒杀商品详情", description = "查询秒杀商品详情")
    public ApiResponse<SeckillProductDTO> getSeckillProduct(@PathVariable Long productId) {
        return seckillProductFeignClient.getProduct(productId);
    }

    @PostMapping("/seckill/products/{activityId}")
    @OperLog(title = "秒杀商品", businessType = 1)
    @RequiresPermission("business:seckill:add")
    @Operation(summary = "添加秒杀商品", description = "为指定活动添加秒杀商品")
    public ApiResponse<SeckillProductDTO> addSeckillProduct(
            @PathVariable Long activityId, @RequestBody AddSeckillProductRequest request) {
        return seckillProductFeignClient.addProduct(activityId, request);
    }

    @DeleteMapping("/seckill/products/{productId}")
    @OperLog(title = "秒杀商品", businessType = 3)
    @RequiresPermission("business:seckill:remove")
    @Operation(summary = "删除秒杀商品", description = "删除秒杀商品")
    public ApiResponse<Void> deleteSeckillProduct(@PathVariable Long productId) {
        return seckillProductFeignClient.deleteProduct(productId);
    }
}
