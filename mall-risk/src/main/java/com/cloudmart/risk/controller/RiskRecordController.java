package com.cloudmart.risk.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.risk.service.RiskRecordService;
import com.cloudmart.risk.vo.RiskRecordVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 风控记录查询（T02）：仅 mall-admin 服务令牌（ROLE_INTERNAL）可读。
 * 记录包含用户行为与判定结论，属于敏感数据，用户端不提供任何直达入口。
 */
@RestController
@RequestMapping("/records")
@PreAuthorize("hasRole('INTERNAL')")
@Tag(name = "风控记录", description = "风控记录查询接口（仅内部服务）")
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
