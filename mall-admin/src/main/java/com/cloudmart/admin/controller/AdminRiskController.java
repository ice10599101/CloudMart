package com.cloudmart.admin.controller;

import com.cloudmart.admin.feign.RiskFeignClient;
import com.cloudmart.common.annotation.OperLog;
import com.cloudmart.common.annotation.RequiresPermission;
import com.cloudmart.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 业务管理-风控域：黑名单、风控记录、风控规则。
 * <p>路径前缀保持历史契约 {@code /business}，与拆分前 AdminBusinessController 完全一致。
 */
@RestController
@RequestMapping("/business")
@Tag(name = "业务管理", description = "管理后台对业务模块的代理接口：风控（黑名单/记录/规则）")
public class AdminRiskController {

    private final RiskFeignClient riskFeignClient;

    public AdminRiskController(RiskFeignClient riskFeignClient) {
        this.riskFeignClient = riskFeignClient;
    }

    // ==================== 风控/黑名单 ====================

    @GetMapping("/risk/blacklist")
    @RequiresPermission("business:risk:list")
    @Operation(summary = "黑名单列表", description = "查询黑名单列表，支持按类型筛选")
    public ApiResponse<Object> listBlacklist(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        Map<String, Object> params = new HashMap<>();
        if (type != null) params.put("type", type);
        params.put("page", page);
        params.put("pageSize", pageSize);
        return riskFeignClient.listBlacklist(params);
    }

    @PostMapping("/risk/blacklist")
    @OperLog(title = "风控管理", businessType = 1)
    @RequiresPermission("business:risk:add")
    @Operation(summary = "添加黑名单", description = "将用户/IP/设备加入黑名单")
    public ApiResponse<Object> addToBlacklist(@RequestBody Map<String, String> body) {
        LocalDateTime expiredAt = null;
        if (body.get("expiredAt") != null && !body.get("expiredAt").isEmpty()) {
            expiredAt = LocalDateTime.parse(body.get("expiredAt"));
        }
        return riskFeignClient.addToBlacklist(body.get("type"), body.get("value"), body.get("reason"), expiredAt);
    }

    @DeleteMapping("/risk/blacklist/{type}/{value}")
    @OperLog(title = "风控管理", businessType = 3)
    @RequiresPermission("business:risk:remove")
    @Operation(summary = "移除黑名单", description = "将用户/IP/设备从黑名单移除")
    public ApiResponse<Void> removeFromBlacklist(@PathVariable("type") String type, @PathVariable("value") String value) {
        return riskFeignClient.removeFromBlacklist(type, value);
    }

    @GetMapping("/risk/blacklist/check")
    @RequiresPermission("business:risk:query")
    @Operation(summary = "检查黑名单", description = "检查指定目标是否在黑名单中")
    public ApiResponse<Boolean> checkBlacklist(@RequestParam("type") String type, @RequestParam("value") String value) {
        return riskFeignClient.checkBlacklist(type, value);
    }

    // ==================== 风控记录 ====================

    @GetMapping("/risk/records")
    @RequiresPermission("business:risk:list")
    @Operation(summary = "风控记录列表", description = "查询风控记录列表")
    public ApiResponse<Object> listRiskRecords(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        Map<String, Object> params = new HashMap<>();
        if (userId != null) params.put("userId", userId);
        params.put("page", page);
        params.put("size", pageSize);
        return riskFeignClient.listRiskRecords(params);
    }

    @GetMapping("/risk/records/{id}")
    @RequiresPermission("business:risk:query")
    @Operation(summary = "风控记录详情", description = "查询风控记录详情")
    public ApiResponse<Object> getRiskRecord(@PathVariable Long id) {
        return riskFeignClient.getRiskRecord(id);
    }

    // ==================== 风控规则 ====================

    @GetMapping("/risk/rules")
    @RequiresPermission("business:risk:list")
    @Operation(summary = "风控规则列表", description = "查询风控规则列表")
    public ApiResponse<Object> listRiskRules() {
        return riskFeignClient.listRiskRules(Map.of());
    }

    @PostMapping("/risk/rules")
    @OperLog(title = "风控管理", businessType = 1)
    @RequiresPermission("business:risk:add")
    @Operation(summary = "创建风控规则", description = "创建风控规则")
    public ApiResponse<Object> createRiskRule(@RequestBody Map<String, Object> body) {
        return riskFeignClient.createRiskRule(body);
    }

    @PutMapping("/risk/rules/{id}")
    @OperLog(title = "风控管理", businessType = 2)
    @RequiresPermission("business:risk:edit")
    @Operation(summary = "更新风控规则", description = "更新风控规则")
    public ApiResponse<Object> updateRiskRule(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return riskFeignClient.updateRiskRule(id, body);
    }

    @DeleteMapping("/risk/rules/{id}")
    @OperLog(title = "风控管理", businessType = 3)
    @RequiresPermission("business:risk:remove")
    @Operation(summary = "删除风控规则", description = "删除风控规则")
    public ApiResponse<Void> deleteRiskRule(@PathVariable Long id) {
        return riskFeignClient.deleteRiskRule(id);
    }
}
