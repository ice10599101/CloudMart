package com.cloudmart.coupon.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.coupon.service.ExchangeCodeService;
import com.cloudmart.coupon.vo.ExchangeCodeFactVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 兑换码内部接口（R01）：向 mall-pet 权益核验提供只读权威事实。
 * 服务令牌强校验（iss=mall-pet、scope=coupon:redeem，见 application.yml
 * service-token-paths 最长前缀规则）；终端不可达（网关封锁 /internal）。
 */
@RestController
@RequestMapping("/internal/exchange-codes")
@Tag(name = "兑换码内部接口", description = "R01：兑换码权威事实只读核验（mall-pet 权益链路）")
@PreAuthorize("hasRole('INTERNAL')")
public class CouponExchangeCodeInternalController {

    private final ExchangeCodeService exchangeCodeService;

    public CouponExchangeCodeInternalController(ExchangeCodeService exchangeCodeService) {
        this.exchangeCodeService = exchangeCodeService;
    }

    @GetMapping("/{code}/fact")
    @Operation(summary = "兑换码权威事实", description = "状态与归属以 exchange_codes 表为准；"
            + "码不存在返回 EXCHANGE_CODE_NOT_FOUND")
    public ApiResponse<ExchangeCodeFactVO> fact(
            @Parameter(description = "兑换码", required = true) @PathVariable("code") String code) {
        return ApiResponse.ok(exchangeCodeService.getFact(code));
    }
}
