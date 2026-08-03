package com.cloudmart.risk.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.risk.entity.BlacklistEntry;
import com.cloudmart.risk.service.BlacklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/blacklist")
@Tag(name = "黑名单管理", description = "黑名单增删查接口")
public class BlacklistController {

    private final BlacklistService blacklistService;

    public BlacklistController(BlacklistService blacklistService) {
        this.blacklistService = blacklistService;
    }

    @PostMapping
    @Operation(summary = "添加黑名单", description = "将用户/IP/设备加入黑名单")
    public ApiResponse<BlacklistEntry> addToBlacklist(
            @Parameter(description = "目标类型: USER/IP/DEVICE") @RequestParam("type") String targetType,
            @Parameter(description = "目标值") @RequestParam("value") String targetValue,
            @Parameter(description = "原因") @RequestParam("reason") String reason,
            @Parameter(description = "过期时间，null表示永久") @RequestParam(value = "expiredAt", required = false) LocalDateTime expiredAt) {
        return ApiResponse.ok(blacklistService.addToBlacklist(targetType, targetValue, reason, expiredAt));
    }

    @DeleteMapping("/{type}/{value}")
    @Operation(summary = "移除黑名单", description = "将用户/IP/设备从黑名单移除")
    public ApiResponse<Void> removeFromBlacklist(
            @Parameter(description = "目标类型") @PathVariable("type") String targetType,
            @Parameter(description = "目标值") @PathVariable("value") String targetValue) {
        blacklistService.removeFromBlacklist(targetType, targetValue);
        return ApiResponse.ok(null);
    }

    @GetMapping("/check")
    @Operation(summary = "检查是否在黑名单", description = "检查指定目标是否在黑名单中")
    public ApiResponse<Boolean> checkBlacklist(
            @Parameter(description = "目标类型") @RequestParam("type") String targetType,
            @Parameter(description = "目标值") @RequestParam("value") String targetValue) {
        return ApiResponse.ok(blacklistService.isBlacklisted(targetType, targetValue));
    }

    @GetMapping("/list")
    @Operation(summary = "黑名单列表", description = "分页查询黑名单列表，支持按类型筛选")
    public ApiResponse<List<BlacklistEntry>> listBlacklist(
            @Parameter(description = "目标类型") @RequestParam(value = "type", required = false) String targetType,
            @Parameter(description = "页码") @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        IPage<BlacklistEntry> result = blacklistService.listBlacklist(targetType, page, pageSize);
        return ApiResponse.ok(result.getRecords(), result.getCurrent(), result.getSize(), result.getTotal());
    }
}
