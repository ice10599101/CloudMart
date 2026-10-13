package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.feign.CouponTemplateDTO;
import com.cloudmart.admin.dto.feign.CreateCouponTemplateRequest;
import com.cloudmart.admin.feign.CouponFeignClient;
import com.cloudmart.admin.feign.ExchangeCodeFeignClient;
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
 * 业务管理-优惠券域：券模板与兑换码。
 * <p>路径前缀保持历史契约 {@code /business}，与拆分前 AdminBusinessController 完全一致。
 */
@RestController
@RequestMapping("/business")
@Tag(name = "业务管理", description = "管理后台对业务模块的代理接口：优惠券/兑换码")
public class AdminCouponController {

    private final CouponFeignClient couponFeignClient;
    private final ExchangeCodeFeignClient exchangeCodeFeignClient;

    public AdminCouponController(CouponFeignClient couponFeignClient,
                                 ExchangeCodeFeignClient exchangeCodeFeignClient) {
        this.couponFeignClient = couponFeignClient;
        this.exchangeCodeFeignClient = exchangeCodeFeignClient;
    }

    // ==================== 优惠券 ====================

    @GetMapping("/coupons")
    @RequiresPermission("business:coupon:list")
    @Operation(summary = "优惠券列表", description = "查询优惠券模板列表")
    public ApiResponse<List<CouponTemplateDTO>> listCoupons(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
        return couponFeignClient.listTemplates(type, status, page, pageSize);
    }

    @GetMapping("/coupons/{id}")
    @RequiresPermission("business:coupon:query")
    @Operation(summary = "优惠券详情", description = "查询优惠券模板详情")
    public ApiResponse<CouponTemplateDTO> getCoupon(@PathVariable Long id) {
        return couponFeignClient.getTemplateById(id);
    }

    @PostMapping("/coupons")
    @OperLog(title = "优惠券管理", businessType = 1)
    @RequiresPermission("business:coupon:add")
    @Operation(summary = "创建优惠券", description = "创建优惠券模板")
    public ApiResponse<CouponTemplateDTO> createCoupon(@RequestBody CreateCouponTemplateRequest request) {
        return couponFeignClient.createTemplate(request);
    }

    @PutMapping("/coupons/{id}")
    @OperLog(title = "优惠券管理", businessType = 2)
    @RequiresPermission("business:coupon:edit")
    @Operation(summary = "更新优惠券", description = "更新优惠券信息")
    public ApiResponse<Object> updateCoupon(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return couponFeignClient.updateTemplate(id, body);
    }

    @PutMapping("/coupons/{id}/disable")
    @OperLog(title = "优惠券管理", businessType = 2)
    @RequiresPermission("business:coupon:disable")
    @Operation(summary = "禁用优惠券", description = "禁用优惠券模板")
    public ApiResponse<CouponTemplateDTO> disableCoupon(@PathVariable Long id) {
        return couponFeignClient.disableTemplate(id);
    }

    @PutMapping("/coupons/{id}/enable")
    @OperLog(title = "优惠券管理", businessType = 2)
    @RequiresPermission("business:coupon:edit")
    @Operation(summary = "启用优惠券", description = "启用已禁用的优惠券模板")
    public ApiResponse<CouponTemplateDTO> enableCoupon(@PathVariable Long id) {
        return couponFeignClient.enableTemplate(id);
    }

    @DeleteMapping("/coupons/{id}")
    @OperLog(title = "优惠券管理", businessType = 3)
    @RequiresPermission("business:coupon:remove")
    @Operation(summary = "删除优惠券", description = "删除优惠券模板")
    public ApiResponse<Void> deleteCoupon(@PathVariable Long id) {
        return couponFeignClient.deleteCoupon(id);
    }

    // ==================== 兑换码 ====================

    @PostMapping("/coupons/exchange-codes/generate")
    @OperLog(title = "兑换码管理", businessType = 1)
    @RequiresPermission("business:coupon:add")
    @Operation(summary = "批量生成兑换码", description = "为指定优惠券模板批量生成，单次上限 1000 张")
    public ApiResponse<Object> generateExchangeCodes(@RequestBody Map<String, Object> body) {
        return exchangeCodeFeignClient.generateBatch(body);
    }

    @GetMapping("/coupons/exchange-codes")
    @RequiresPermission("business:coupon:list")
    @Operation(summary = "兑换码列表", description = "分页查询指定模板的兑换码，支持状态筛选")
    public ApiResponse<Object> listExchangeCodes(
            @RequestParam("templateId") Long templateId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        return exchangeCodeFeignClient.listExchangeCodes(templateId, status, page, pageSize);
    }

    @GetMapping("/coupons/exchange-codes/{code}")
    @RequiresPermission("business:coupon:query")
    @Operation(summary = "兑换码详情", description = "根据兑换码字符串查询详情")
    public ApiResponse<Object> getExchangeCode(@PathVariable String code) {
        return exchangeCodeFeignClient.getExchangeCode(code);
    }

    @PutMapping("/coupons/exchange-codes/{code}/disable")
    @OperLog(title = "兑换码管理", businessType = 2)
    @RequiresPermission("business:coupon:disable")
    @Operation(summary = "作废兑换码", description = "作废未兑换的兑换码；已兑换的不允许作废")
    public ApiResponse<Void> disableExchangeCode(@PathVariable String code) {
        return exchangeCodeFeignClient.disableExchangeCode(code);
    }
}
