package com.cloudmart.coupon.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.api.ApiResponse.Meta;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.coupon.converter.CouponTemplateConverter;
import com.cloudmart.coupon.dto.CreateCouponTemplateRequest;
import com.cloudmart.coupon.dto.CouponTemplateDTO;
import com.cloudmart.coupon.entity.CouponTemplate;
import com.cloudmart.coupon.repository.CouponTemplateMapper;
import com.cloudmart.coupon.service.CouponService;
import com.cloudmart.coupon.vo.CouponTemplateVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/coupon-templates")
@Tag(name = "优惠券模板管理(后台)", description = "管理后台优惠券模板管理接口，仅供内部服务调用")
@RequiredArgsConstructor
public class AdminCouponController {

    private final CouponService couponService;
    private final CouponTemplateConverter couponTemplateConverter;
    private final CouponTemplateMapper couponTemplateMapper;

    @GetMapping
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "查询优惠券模板列表", description = "管理后台分页查询优惠券模板，支持按类型和状态筛选")
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
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "查询优惠券模板详情", description = "管理后台根据ID查询优惠券模板详情")
    public ApiResponse<CouponTemplateVO> getTemplateById(
            @Parameter(description = "模板ID") @PathVariable("id") Long id) {
        CouponTemplateDTO dto = couponService.getTemplateById(id);
        return ApiResponse.ok(couponTemplateConverter.dtoToVO(dto));
    }

    @PostMapping
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "创建优惠券模板", description = "管理后台创建优惠券模板")
    public ApiResponse<CouponTemplateVO> createTemplate(
            @Valid @RequestBody CreateCouponTemplateRequest request) {
        CouponTemplateDTO dto = couponService.createTemplate(request);
        return ApiResponse.ok(couponTemplateConverter.dtoToVO(dto));
    }

    @PutMapping("/{id}/disable")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "禁用优惠券模板", description = "管理后台禁用优惠券模板")
    public ApiResponse<CouponTemplateVO> disableTemplate(
            @Parameter(description = "模板ID") @PathVariable("id") Long id) {
        CouponTemplateDTO dto = couponService.disableTemplate(id);
        return ApiResponse.ok(couponTemplateConverter.dtoToVO(dto));
    }

    @PutMapping("/{id}/enable")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "启用已禁用的优惠券模板", description = "管理后台启用已禁用的优惠券模板")
    public ApiResponse<CouponTemplateVO> enableTemplate(
            @Parameter(description = "模板ID") @PathVariable("id") Long id) {
        CouponTemplateDTO dto = couponService.enableTemplate(id);
        return ApiResponse.ok(couponTemplateConverter.dtoToVO(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "更新优惠券模板", description = "管理后台更新优惠券模板信息")
    public ApiResponse<CouponTemplateVO> updateTemplate(
            @Parameter(description = "模板ID") @PathVariable("id") Long id,
            @RequestBody Map<String, Object> body) {
        CouponTemplate template = couponTemplateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException("TEMPLATE_NOT_FOUND", "优惠券模板不存在");
        }
        if (body.containsKey("name")) {
            template.setName((String) body.get("name"));
        }
        if (body.containsKey("type")) {
            template.setType((String) body.get("type"));
        }
        if (body.containsKey("thresholdAmount")) {
            template.setThresholdAmount(new BigDecimal(body.get("thresholdAmount").toString()));
        }
        if (body.containsKey("discountAmount")) {
            template.setDiscountAmount(new BigDecimal(body.get("discountAmount").toString()));
        }
        if (body.containsKey("discountRate")) {
            template.setDiscountRate(new BigDecimal(body.get("discountRate").toString()));
        }
        if (body.containsKey("totalQuantity")) {
            template.setTotalQuantity(((Number) body.get("totalQuantity")).intValue());
        }
        if (body.containsKey("remainingQuantity")) {
            template.setRemainingQuantity(((Number) body.get("remainingQuantity")).intValue());
        }
        if (body.containsKey("perUserLimit")) {
            template.setPerUserLimit(((Number) body.get("perUserLimit")).intValue());
        }
        if (body.containsKey("validityType")) {
            template.setValidityType((String) body.get("validityType"));
        }
        if (body.containsKey("startTime")) {
            template.setStartTime(LocalDateTime.parse(body.get("startTime").toString()));
        }
        if (body.containsKey("endTime")) {
            template.setEndTime(LocalDateTime.parse(body.get("endTime").toString()));
        }
        if (body.containsKey("validDays")) {
            template.setValidDays(((Number) body.get("validDays")).intValue());
        }
        if (body.containsKey("status")) {
            template.setStatus((String) body.get("status"));
        }
        couponTemplateMapper.updateById(template);
        return ApiResponse.ok(couponTemplateConverter.toVO(template));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "删除优惠券模板", description = "管理后台删除优惠券模板")
    public ApiResponse<Void> deleteTemplate(
            @Parameter(description = "模板ID") @PathVariable("id") Long id) {
        CouponTemplate template = couponTemplateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException("TEMPLATE_NOT_FOUND", "优惠券模板不存在");
        }
        couponTemplateMapper.deleteById(id);
        return ApiResponse.ok(null);
    }
}
