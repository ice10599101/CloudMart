package com.cloudmart.risk.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.risk.dto.RiskCheckRequest;
import com.cloudmart.risk.service.RiskCheckService;
import com.cloudmart.risk.vo.RiskCheckVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 风控检查（T02）。
 *
 * <p>只保留 {@code POST /check/internal}：调用方必须是 mall-order / mall-coupon / mall-payment，
 * 由服务令牌建立 ROLE_INTERNAL。原公开 {@code POST /check} 允许任意已登录用户提交自定义 userId
 * 执行风控判定，属于把内部判定能力开放给用户，且没有真实产品消费者，已按 T02 删除。</p>
 *
 * <p>服务令牌只解决“调用方是谁”，不构成授权：类级 {@code hasRole('INTERNAL')} 才是拒绝用户 JWT 的边界。</p>
 */
@RestController
@RequestMapping("/check")
@PreAuthorize("hasRole('INTERNAL')")
@Tag(name = "风控检查", description = "下单/领券/支付前置风控检查（仅内部服务）")
public class RiskCheckController {

    private final RiskCheckService riskCheckService;

    public RiskCheckController(RiskCheckService riskCheckService) {
        this.riskCheckService = riskCheckService;
    }

    @PostMapping("/internal")
    @Operation(summary = "内部风控检查", description = "下单/领券/支付创建前置检查（服务令牌可达）")
    public ApiResponse<RiskCheckVO> checkInternal(@Valid @RequestBody RiskCheckRequest request) {
        return ApiResponse.ok(riskCheckService.check(request));
    }
}
