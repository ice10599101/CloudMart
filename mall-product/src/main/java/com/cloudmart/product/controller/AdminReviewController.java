package com.cloudmart.product.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.api.ApiResponse.Meta;
import com.cloudmart.product.converter.ProductConverter;
import com.cloudmart.product.dto.ReviewDTO;
import com.cloudmart.product.dto.ReviewStatsDTO;
import com.cloudmart.product.service.ReviewService;
import com.cloudmart.product.vo.ReviewStatsVO;
import com.cloudmart.product.vo.ReviewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/reviews")
@Tag(name = "评价管理(后台)", description = "管理后台评价管理接口，仅供内部服务调用")
@RequiredArgsConstructor
public class AdminReviewController {

    private final ReviewService reviewService;
    private final ProductConverter productConverter;

    @GetMapping
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "评价列表", description = "管理后台分页查询评价列表，支持按商品ID和状态筛选")
    public ApiResponse<Map<String, Object>> listReviews(
            @Parameter(description = "商品ID") @RequestParam(required = false) Long productId,
            @Parameter(description = "评价状态") @RequestParam(required = false) Integer status,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int pageSize) {
        Page<ReviewDTO> result = reviewService.listReviewsForAdmin(productId, status, page, pageSize);
        List<ReviewVO> voList = productConverter.reviewDtoListToVOList(result.getRecords());
        Map<String, Object> data = new HashMap<>();
        data.put("records", voList);
        data.put("total", result.getTotal());
        data.put("page", result.getCurrent());
        data.put("pageSize", result.getSize());
        return ApiResponse.ok(data);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "评价详情", description = "管理后台查询评价详情")
    public ApiResponse<ReviewVO> getReview(
            @Parameter(description = "评价ID", required = true) @PathVariable("id") Long id) {
        ReviewDTO dto = reviewService.getReviewById(id);
        return ApiResponse.ok(productConverter.reviewDtoToVO(dto));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "更新评价状态（R08 下架必填理由）",
            description = "0=下架（reason 必填，作者可见），1=恢复显示（清空理由）")
    public ApiResponse<Void> updateReviewStatus(
            @Parameter(description = "评价ID", required = true) @PathVariable("id") Long id,
            @RequestBody(required = false) ReviewStatusRequest body,
            // 兼容旧 query 参数调用（无理由下架将被服务端拒绝，强制走带理由的新调用方）
            @Parameter(description = "状态值（旧 query 兼容）") @RequestParam(value = "status", required = false) Integer queryStatus) {
        Integer status = body != null ? body.status() : queryStatus;
        String reason = body != null ? body.reason() : null;
        reviewService.updateReviewStatus(id, status, reason);
        return ApiResponse.ok(null);
    }

    /** R08 评价审核/下架请求体 */
    public record ReviewStatusRequest(Integer status, String reason) {
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "删除评价", description = "管理后台删除评价")
    public ApiResponse<Void> deleteReview(
            @Parameter(description = "评价ID", required = true) @PathVariable("id") Long id) {
        reviewService.deleteReview(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/stats/{productId}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "评价统计", description = "管理后台查询商品评价统计信息")
    public ApiResponse<ReviewStatsVO> getReviewStats(
            @Parameter(description = "商品ID", required = true) @PathVariable("productId") Long productId) {
        ReviewStatsDTO dto = reviewService.getReviewStats(productId);
        return ApiResponse.ok(productConverter.reviewStatsDtoToVO(dto));
    }
}
