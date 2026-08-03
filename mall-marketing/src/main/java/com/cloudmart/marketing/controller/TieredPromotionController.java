package com.cloudmart.marketing.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.marketing.converter.MarketingConverter;
import com.cloudmart.marketing.dto.CalculateDiscountRequest;
import com.cloudmart.marketing.dto.CalculateDiscountResult;
import com.cloudmart.marketing.dto.TieredPromotionDTO;
import com.cloudmart.marketing.service.TieredPromotionService;
import com.cloudmart.marketing.vo.TieredPromotionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@Tag(name = "阶梯满减", description = "阶梯满减用户端接口")
@RestController
@RequestMapping("/tiered")
public class TieredPromotionController {

    private final TieredPromotionService tieredPromotionService;
    private final MarketingConverter marketingConverter;

    public TieredPromotionController(TieredPromotionService tieredPromotionService, MarketingConverter marketingConverter) {
        this.tieredPromotionService = tieredPromotionService;
        this.marketingConverter = marketingConverter;
    }

    @Operation(summary = "获取满减活动详情")
    @GetMapping("/promotions/{id}")
    public ApiResponse<TieredPromotionVO> getPromotion(
            @Parameter(description = "活动ID") @PathVariable Long id) {
        TieredPromotionDTO dto = tieredPromotionService.getPromotion(id);
        return ApiResponse.ok(marketingConverter.tieredPromotionDtoToVOWithRules(dto));
    }

    @Operation(summary = "计算满减优惠")
    @PostMapping("/calculate")
    public ApiResponse<CalculateDiscountResult> calculateDiscount(
            @Valid @RequestBody CalculateDiscountRequest request) {
        return ApiResponse.ok(tieredPromotionService.calculateDiscount(request));
    }
}
