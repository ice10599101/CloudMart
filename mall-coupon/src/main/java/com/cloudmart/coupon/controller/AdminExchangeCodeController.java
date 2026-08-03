package com.cloudmart.coupon.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.api.ApiResponse.Meta;
import com.cloudmart.coupon.dto.GenerateExchangeCodeRequest;
import com.cloudmart.coupon.service.ExchangeCodeService;
import com.cloudmart.coupon.vo.ExchangeCodeVO;
import com.cloudmart.coupon.vo.ExchangeCodeVO.BatchGenerateResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 兑换码管理接口（后台）
 * <p>
 * 供运营后台调用，生成、查询、作废兑换码。所有接口限内部服务调用。
 * </p>
 */
@RestController
@RequestMapping("/admin/exchange-codes")
@Tag(name = "兑换码管理(后台)", description = "兑换码生成、查询、作废接口，仅供内部服务调用")
@RequiredArgsConstructor
public class AdminExchangeCodeController {

    private final ExchangeCodeService exchangeCodeService;

    @PostMapping("/generate")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "批量生成兑换码", description = "为指定优惠券模板批量生成兑换码，单次上限1000张")
    public ApiResponse<BatchGenerateResult> generateBatch(
            @Valid @RequestBody GenerateExchangeCodeRequest request) {
        BatchGenerateResult result = exchangeCodeService.generateBatch(request.templateId(), request.quantity());
        return ApiResponse.ok(result);
    }

    @GetMapping("/{code}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "查询兑换码详情", description = "根据兑换码字符串查询详情")
    public ApiResponse<ExchangeCodeVO> getByCode(
            @Parameter(description = "兑换码") @PathVariable("code") String code) {
        return ApiResponse.ok(exchangeCodeService.getByCode(code));
    }

    @GetMapping
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "查询兑换码列表", description = "分页查询指定模板的兑换码，支持按状态筛选")
    public ApiResponse<List<ExchangeCodeVO>> listByTemplate(
            @Parameter(description = "优惠券模板ID") @RequestParam("templateId") Long templateId,
            @Parameter(description = "状态") @RequestParam(value = "status", required = false) String status,
            @Parameter(description = "页码") @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        List<ExchangeCodeVO> list = exchangeCodeService.listByTemplate(templateId, status, page, pageSize);
        long total = exchangeCodeService.countByTemplate(templateId, status);
        return ApiResponse.ok(list, new Meta(page, pageSize, total));
    }

    @PutMapping("/{code}/disable")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "作废兑换码", description = "作废未兑换的兑换码，已兑换的码不允许作废")
    public ApiResponse<Void> disable(
            @Parameter(description = "兑换码") @PathVariable("code") String code) {
        exchangeCodeService.disable(code);
        return ApiResponse.ok(null);
    }
}
