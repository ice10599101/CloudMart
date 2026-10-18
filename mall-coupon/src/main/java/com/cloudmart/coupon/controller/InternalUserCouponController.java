package com.cloudmart.coupon.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.coupon.converter.UserCouponConverter;
import com.cloudmart.coupon.dto.UserCouponDTO;
import com.cloudmart.coupon.service.CouponService;
import com.cloudmart.coupon.vo.UserCouponVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 优惠券内部查询（T03）。
 *
 * <p>订单服务在报价/建单时按券 ID 读取权威券事实，需要跨用户读取（订单归属由订单服务校验），
 * 因此不能走用户侧 {@code GET /user-coupons/{id}} 的本人过滤。本入口只允许 mall-order
 * 服务令牌（inbound {@code /internal} → mall-order / coupon:trade），用户 JWT 一律 403。</p>
 */
@RestController
@RequestMapping("/internal/user-coupons")
@PreAuthorize("hasRole('INTERNAL')")
@Tag(name = "优惠券内部查询", description = "订单服务专用券事实查询（仅内部服务）")
public class InternalUserCouponController {

    private final CouponService couponService;
    private final UserCouponConverter userCouponConverter;

    public InternalUserCouponController(CouponService couponService, UserCouponConverter userCouponConverter) {
        this.couponService = couponService;
        this.userCouponConverter = userCouponConverter;
    }

    @GetMapping("/{id}")
    @Operation(summary = "按ID查询用户优惠券（内部）", description = "订单报价/建单读取券权威事实")
    public ApiResponse<UserCouponVO> getById(
            @Parameter(description = "用户优惠券ID") @PathVariable("id") Long id) {
        UserCouponDTO dto = couponService.getUserCouponById(id);
        return ApiResponse.ok(userCouponConverter.dtoToVO(dto));
    }
}
