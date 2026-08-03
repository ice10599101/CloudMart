package com.cloudmart.risk.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.risk.dto.RiskCheckRequest;
import com.cloudmart.risk.service.RiskCheckService;
import com.cloudmart.risk.vo.RiskCheckVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/check")
@Tag(name = "风控检查", description = "风控检查接口")
public class RiskCheckController {

    private final RiskCheckService riskCheckService;

    public RiskCheckController(RiskCheckService riskCheckService) {
        this.riskCheckService = riskCheckService;
    }

    @PostMapping
    @Operation(summary = "执行风控检查", description = "对用户操作执行风控检查")
    public ApiResponse<RiskCheckVO> check(@Valid @RequestBody RiskCheckRequest request) {
        return ApiResponse.ok(riskCheckService.check(request));
    }
}
