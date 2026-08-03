package com.cloudmart.coupon.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.api.ApiResponse.Meta;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.coupon.converter.UserCouponConverter;
import com.cloudmart.coupon.dto.ReturnCouponRequest;
import com.cloudmart.coupon.dto.UseCouponRequest;
import com.cloudmart.coupon.dto.UserCouponDTO;
import com.cloudmart.coupon.service.CouponRecommendationService;
import com.cloudmart.coupon.service.CouponService;
import com.cloudmart.coupon.service.ExchangeCodeService;
import com.cloudmart.coupon.vo.RecommendationVO;
import com.cloudmart.coupon.vo.UserCouponVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/user-coupons")
@Tag(name = "用户优惠券管理", description = "优惠券的领取、查询、使用、退还、推荐接口")
public class CouponUserController {

    private final CouponService couponService;
    private final CouponRecommendationService couponRecommendationService;
    private final ExchangeCodeService exchangeCodeService;
    private final UserCouponConverter userCouponConverter;

    public CouponUserController(CouponService couponService,
                                CouponRecommendationService couponRecommendationService,
                                ExchangeCodeService exchangeCodeService,
                                UserCouponConverter userCouponConverter) {
        this.couponService = couponService;
        this.couponRecommendationService = couponRecommendationService;
        this.exchangeCodeService = exchangeCodeService;
        this.userCouponConverter = userCouponConverter;
    }

    @PostMapping("/claim")
    @Operation(summary = "领取优惠券", description = "用户领取指定模板的优惠券")
    public ApiResponse<UserCouponVO> claimCoupon(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "优惠券模板ID") @RequestParam(value = "templateId") Long templateId) {
        UserCouponDTO dto = couponService.claimCoupon(userId, templateId);
        return ApiResponse.ok(userCouponConverter.dtoToVO(dto));
    }

    @GetMapping
    @Operation(summary = "查询用户优惠券列表", description = "分页查询当前用户的优惠券，支持按状态筛选")
    public ApiResponse<List<UserCouponVO>> listUserCoupons(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "优惠券状态") @RequestParam(value = "status", required = false) String status,
            @Parameter(description = "页码") @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        List<UserCouponDTO> dtos = couponService.listUserCoupons(userId, status, page, pageSize);
        long total = couponService.countUserCoupons(userId, status);
        List<UserCouponVO> voList = dtos.stream().map(userCouponConverter::dtoToVO).toList();
        return ApiResponse.ok(voList, new Meta(page, pageSize, total));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询用户优惠券详情", description = "根据ID查询用户优惠券详情（内部调用）")
    public ApiResponse<UserCouponVO> getUserCouponById(
            @Parameter(description = "用户优惠券ID") @PathVariable("id") Long id) {
        UserCouponDTO dto = couponService.getUserCouponById(id);
        return ApiResponse.ok(userCouponConverter.dtoToVO(dto));
    }

    @PostMapping("/use")
    @Operation(summary = "使用优惠券", description = "核销用户优惠券，关联订单ID（内部调用）")
    public ApiResponse<Void> useCoupon(@Valid @RequestBody UseCouponRequest request) {
        couponService.useCoupon(request.userCouponId(), request.orderId());
        return ApiResponse.ok(null);
    }

    @PostMapping("/return")
    @Operation(summary = "退还优惠券", description = "退还已使用的优惠券，取消订单时调用（内部调用）")
    public ApiResponse<Void> returnCoupon(@Valid @RequestBody ReturnCouponRequest request) {
        couponService.returnCoupon(request.userCouponId(), request.orderId());
        return ApiResponse.ok(null);
    }

    @GetMapping("/recommend")
    @Operation(summary = "优惠券推荐", description = "根据订单金额推荐最优优惠券使用方案，支持多券叠加")
    public ApiResponse<RecommendationVO> recommendCoupons(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "订单金额") @RequestParam("orderAmount") BigDecimal orderAmount) {
        RecommendationVO vo = couponRecommendationService.recommend(userId, orderAmount);
        return ApiResponse.ok(vo);
    }

    @PostMapping("/exchange")
    @Operation(summary = "兑换码兑换优惠券", description = "用户输入兑换码，校验通过后发放对应优惠券")
    public ApiResponse<Long> exchangeCode(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "兑换码") @RequestParam("code") String code) {
        Long userCouponId = exchangeCodeService.exchange(userId, code);
        return ApiResponse.ok(userCouponId);
    }
}
