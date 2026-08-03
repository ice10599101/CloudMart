package com.cloudmart.risk.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.risk.dto.CreateRiskRuleRequest;
import com.cloudmart.risk.dto.UpdateRiskRuleRequest;
import com.cloudmart.risk.service.RiskRuleService;
import com.cloudmart.risk.vo.RiskRuleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/rules")
@Tag(name = "风控规则", description = "风控规则的增删改查接口")
public class RiskRuleController {

    private final RiskRuleService riskRuleService;

    public RiskRuleController(RiskRuleService riskRuleService) {
        this.riskRuleService = riskRuleService;
    }

    @PostMapping
    @Operation(summary = "创建风控规则", description = "创建新的风控规则")
    public ApiResponse<RiskRuleVO> createRule(@Valid @RequestBody CreateRiskRuleRequest request) {
        return ApiResponse.ok(riskRuleService.createRule(request));
    }

    @GetMapping
    @Operation(summary = "查询风控规则列表", description = "查询所有风控规则")
    public ApiResponse<List<RiskRuleVO>> listRules() {
        return ApiResponse.ok(riskRuleService.listRules());
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询风控规则详情", description = "根据ID查询风控规则详情")
    public ApiResponse<RiskRuleVO> getRule(@PathVariable Long id) {
        return ApiResponse.ok(riskRuleService.getRule(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新风控规则", description = "更新风控规则配置")
    public ApiResponse<RiskRuleVO> updateRule(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRiskRuleRequest request) {
        return ApiResponse.ok(riskRuleService.updateRule(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除风控规则", description = "删除风控规则")
    public ApiResponse<Void> deleteRule(@PathVariable Long id) {
        riskRuleService.deleteRule(id);
        return ApiResponse.ok(null);
    }
}
