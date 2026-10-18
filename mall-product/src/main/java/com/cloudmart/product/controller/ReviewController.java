package com.cloudmart.product.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.api.ApiResponse.Meta;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.product.converter.ProductConverter;
import com.cloudmart.product.dto.CreateReviewRequest;
import com.cloudmart.product.dto.ReviewDTO;
import com.cloudmart.product.dto.ReviewStatsDTO;
import com.cloudmart.product.service.ReviewService;
import com.cloudmart.product.vo.ReviewStatsVO;
import com.cloudmart.product.vo.ReviewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/reviews")
@Tag(name = "商品评价", description = "商品评价提交与查询接口")
public class ReviewController {

    private final ReviewService reviewService;
    private final ProductConverter productConverter;

    public ReviewController(ReviewService reviewService, ProductConverter productConverter) {
        this.reviewService = reviewService;
        this.productConverter = productConverter;
    }

    @PostMapping
    @Operation(summary = "提交评价", description = "用户对已完成订单的商品提交评价")
    public ApiResponse<ReviewVO> createReview(
            @Parameter(description = "创建评价请求") @Valid @RequestBody CreateReviewRequest request) {
        Long userId = getCurrentUserId();
        ReviewDTO dto = reviewService.createReview(userId, request);
        return ApiResponse.ok(productConverter.reviewDtoToVO(dto));
    }

    @GetMapping("/product/{productId}")
    @Operation(summary = "商品评价列表", description = "分页查询商品的评价列表")
    public ApiResponse<List<ReviewVO>> getProductReviews(
            @Parameter(description = "商品ID", required = true) @PathVariable("productId") Long productId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @Parameter(description = "每页数量（旧参数名，兼容期保留）") @RequestParam(value = "size", required = false) Integer size) {
        int resolvedSize = pageSize != null ? pageSize : (size != null ? size : 10);
        Page<ReviewDTO> result = reviewService.getProductReviews(productId, page, resolvedSize);
        Meta meta = new Meta((int) result.getCurrent(), (int) result.getSize(), result.getTotal());
        return ApiResponse.ok(productConverter.reviewDtoListToVOList(result.getRecords()), meta);
    }

    @GetMapping("/stats/{productId}")
    @Operation(summary = "评价统计", description = "查询商品的评价统计数据（平均评分、各星级数量）")
    public ApiResponse<ReviewStatsVO> getReviewStats(
            @Parameter(description = "商品ID", required = true) @PathVariable("productId") Long productId) {
        ReviewStatsDTO dto = reviewService.getReviewStats(productId);
        return ApiResponse.ok(productConverter.reviewStatsDtoToVO(dto));
    }

    @GetMapping("/check")
    @Operation(summary = "检查评价状态", description = "检查用户是否已对某订单的某商品评价过")
    public ApiResponse<Map<String, Boolean>> checkReview(
            @Parameter(description = "订单ID", required = true) @RequestParam Long orderId,
            @Parameter(description = "商品ID", required = true) @RequestParam Long productId) {
        Long userId = getCurrentUserId();
        boolean reviewed = reviewService.hasUserReviewedProduct(userId, orderId, productId);
        return ApiResponse.ok(Map.of("hasReviewed", reviewed));
    }

    /**
     * R08：按订单行查作者自己的评价（订单详情"评价/查看评价/下架理由"用）；未评价 data=null。
     */
    @GetMapping("/order-item/{orderItemId}")
    @Operation(summary = "查询我在某订单行的评价", description = "含订单行锚点与下架理由；未评价返回 data=null")
    public ApiResponse<ReviewVO> getOwnReviewByOrderItem(
            @Parameter(description = "订单行 ID", required = true) @PathVariable("orderItemId") Long orderItemId) {
        ReviewDTO dto = reviewService.getOwnReviewByOrderItem(getCurrentUserId(), orderItemId);
        return ApiResponse.ok(dto == null ? null : productConverter.reviewDtoToVO(dto));
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new BusinessException("UNAUTHORIZED", "未登录或登录已过期");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof String principalStr) {
            try {
                return Long.parseLong(principalStr);
            } catch (NumberFormatException e) {
                throw new BusinessException("UNAUTHORIZED", "内部服务调用缺少用户标识");
            }
        }
        throw new BusinessException("UNAUTHORIZED", "无法获取用户信息");
    }
}
