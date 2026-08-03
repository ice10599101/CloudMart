package com.cloudmart.risk.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.risk.service.RiskRecordService;
import com.cloudmart.risk.vo.RiskRecordVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/records")
@Tag(name = "风控记录", description = "风控记录查询接口")
public class RiskRecordController {

    private final RiskRecordService riskRecordService;

    public RiskRecordController(RiskRecordService riskRecordService) {
        this.riskRecordService = riskRecordService;
    }

    @GetMapping
    @Operation(summary = "查询风控记录", description = "分页查询风控记录，支持按用户ID筛选")
    public ApiResponse<List<RiskRecordVO>> listRecords(
            @Parameter(description = "用户ID") @RequestParam(required = false) Long userId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(riskRecordService.listRecords(userId, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询风控记录详情", description = "根据ID查询风控记录详情")
    public ApiResponse<RiskRecordVO> getRecord(@PathVariable Long id) {
        return ApiResponse.ok(riskRecordService.getRecord(id));
    }
}
