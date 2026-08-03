package com.cloudmart.coupon.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.api.ApiResponse.Meta;
import com.cloudmart.coupon.converter.CouponTemplateConverter;
import com.cloudmart.coupon.dto.CreateCouponTemplateRequest;
import com.cloudmart.coupon.dto.CouponTemplateDTO;
import com.cloudmart.coupon.service.CouponService;
import com.cloudmart.coupon.vo.CouponTemplateVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/coupon-templates")
@Tag(name = "优惠券模板管理", description = "优惠券模板的创建、查询、禁用接口")
public class CouponController {

    private final CouponService couponService;
    private final CouponTemplateConverter couponTemplateConverter;

    public CouponController(CouponService couponService, CouponTemplateConverter couponTemplateConverter) {
        this.couponService = couponService;
        this.couponTemplateConverter = couponTemplateConverter;
    }

    @PostMapping
    @Operation(summary = "创建优惠券模板", description = "创建新的优惠券模板，支持满减券和折扣券")
    public ApiResponse<CouponTemplateVO> createTemplate(
            @Valid @RequestBody CreateCouponTemplateRequest request) {
        CouponTemplateDTO dto = couponService.createTemplate(request);
        return ApiResponse.ok(couponTemplateConverter.dtoToVO(dto));
    }

    @GetMapping
    @Operation(summary = "查询优惠券模板列表", description = "分页查询优惠券模板，支持按类型和状态筛选")
    public ApiResponse<List<CouponTemplateVO>> listTemplates(
            @Parameter(description = "优惠券类型") @RequestParam(value = "type", required = false) String type,
            @Parameter(description = "状态") @RequestParam(value = "status", required = false) String status,
            @Parameter(description = "页码") @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        List<CouponTemplateDTO> dtos = couponService.listTemplates(type, status, page, pageSize);
        long total = couponService.countTemplates(type, status);
        return ApiResponse.ok(couponTemplateConverter.dtoListToVOList(dtos), new Meta(page, pageSize, total));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询优惠券模板详情", description = "根据ID查询优惠券模板详情")
    public ApiResponse<CouponTemplateVO> getTemplateById(
            @Parameter(description = "模板ID") @PathVariable("id") Long id) {
        CouponTemplateDTO dto = couponService.getTemplateById(id);
        return ApiResponse.ok(couponTemplateConverter.dtoToVO(dto));
    }

    @PutMapping("/{id}/disable")
    @Operation(summary = "禁用优惠券模板", description = "将启用的优惠券模板设为禁用状态")
    public ApiResponse<CouponTemplateVO> disableTemplate(
            @Parameter(description = "模板ID") @PathVariable("id") Long id) {
        CouponTemplateDTO dto = couponService.disableTemplate(id);
        return ApiResponse.ok(couponTemplateConverter.dtoToVO(dto));
    }
}
