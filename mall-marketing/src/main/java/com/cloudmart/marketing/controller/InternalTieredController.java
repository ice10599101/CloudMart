package com.cloudmart.marketing.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.marketing.dto.TieredMatchRequest;
import com.cloudmart.marketing.dto.TieredMatchResult;
import com.cloudmart.marketing.service.TieredPromotionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * R12：内部满减自动匹配（mall-order 报价链路）——报价/下单重算同一入口，
 * 保证"商品金额→满减→券→应付"在下单锁定与报价展示时结果一致。
 */
@RestController
@RequestMapping("/internal/tiered")
@Tag(name = "内部-阶梯满减", description = "报价链路满减自动匹配（服务令牌可达）")
@PreAuthorize("hasRole('INTERNAL')")
public class InternalTieredController {

    private final TieredPromotionService tieredPromotionService;

    public InternalTieredController(TieredPromotionService tieredPromotionService) {
        this.tieredPromotionService = tieredPromotionService;
    }

    @Operation(summary = "购物篮自动匹配最优满减（多活动重叠取优惠最大者）")
    @PostMapping("/match")
    public ApiResponse<TieredMatchResult> match(@RequestBody TieredMatchRequest request) {
        return ApiResponse.ok(tieredPromotionService.matchBest(request));
    }
}
