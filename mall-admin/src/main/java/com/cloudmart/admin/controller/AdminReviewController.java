package com.cloudmart.admin.controller;

import com.cloudmart.admin.feign.ReviewFeignClient;
import com.cloudmart.common.annotation.OperLog;
import com.cloudmart.common.annotation.RequiresPermission;
import com.cloudmart.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 业务管理-评价域：商品评价查询、上下架（R08 下架理由强校验透传）、删除、统计。
 * <p>路径前缀保持历史契约 {@code /business}，与拆分前 AdminBusinessController 完全一致。
 */
@RestController
@RequestMapping("/business")
@Tag(name = "业务管理", description = "管理后台对业务模块的代理接口：评价管理")
public class AdminReviewController {

    private final ReviewFeignClient reviewFeignClient;

    public AdminReviewController(ReviewFeignClient reviewFeignClient) {
        this.reviewFeignClient = reviewFeignClient;
    }

    // ==================== 评价 ====================

    @GetMapping("/reviews")
    @RequiresPermission("business:review:list")
    @Operation(summary = "评价列表", description = "查询所有评价，支持按商品ID和状态筛选")
    public ApiResponse<Object> listReviews(
            @RequestParam(value = "productId", required = false) Long productId,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
        return reviewFeignClient.listReviews(productId, status, page, pageSize);
    }

    @GetMapping("/reviews/{id}")
    @RequiresPermission("business:review:query")
    @Operation(summary = "评价详情", description = "查询评价详情")
    public ApiResponse<Object> getReview(@PathVariable Long id) {
        return reviewFeignClient.getReview(id);
    }

    @PutMapping("/reviews/{id}/status")
    @OperLog(title = "评价管理", businessType = 2)
    @RequiresPermission("business:review:edit")
    @Operation(summary = "更新评价状态", description = "R08 下架必须填写理由（作者可见）；恢复显示清空理由")
    public ApiResponse<Void> updateReviewStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        // 原样透传 {status, reason}，由 mall-product 强校验下架理由
        return reviewFeignClient.updateReviewStatus(id, body);
    }

    @DeleteMapping("/reviews/{id}")
    @OperLog(title = "评价管理", businessType = 3)
    @RequiresPermission("business:review:remove")
    @Operation(summary = "删除评价", description = "删除评价")
    public ApiResponse<Void> deleteReview(@PathVariable Long id) {
        return reviewFeignClient.deleteReview(id);
    }

    @GetMapping("/reviews/stats/{productId}")
    @RequiresPermission("business:review:list")
    @Operation(summary = "评价统计", description = "查询商品评价统计")
    public ApiResponse<Object> getReviewStats(@PathVariable Long productId) {
        return reviewFeignClient.getReviewStats(productId);
    }
}
