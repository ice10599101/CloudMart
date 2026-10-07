package com.cloudmart.wish.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wish.entity.LbsFreeze;
import com.cloudmart.wish.entity.LbsSuspicious;
import com.cloudmart.wish.service.EncounterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理后台擦肩而过风控 Controller（Sprint 3.3 管理后台：
 * 位置伪造检测面板 + 冻结列表/解冻 + 轨迹清理监控）。
 *
 * <p>路由前缀 /admin/encounter，仅内部服务调用；权限点
 * {@code business:encounter:audit} / {@code business:encounter:unfreeze}。</p>
 */
@RestController
@RequestMapping("/admin/encounter")
@PreAuthorize("hasRole('INTERNAL')")
@Tag(name = "管理后台-擦肩而过风控", description = "可疑跳跃记录/冻结列表/解冻（Sprint 3.3）")
@RequiredArgsConstructor
public class AdminEncounterController {

    private final EncounterService encounterService;

    @GetMapping("/suspicious")
    @Operation(summary = "可疑跳跃记录", description = "userId 过滤可选，24h 内记录 id 倒序，仅含 geohash 无原始坐标")
    public ApiResponse<List<LbsSuspicious>> listSuspicious(
            @Parameter(description = "用户 ID 过滤") @RequestParam(required = false) Long userId) {
        return ApiResponse.ok(encounterService.listSuspicious(userId));
    }

    @GetMapping("/freezes")
    @Operation(summary = "冻结用户列表", description = "当前未解冻的冻结记录")
    public ApiResponse<List<LbsFreeze>> listFreezes() {
        return ApiResponse.ok(encounterService.listFreezes());
    }

    @PostMapping("/freezes/{userId}/unfreeze")
    @Operation(summary = "解冻", description = "删除冻结记录，用户立即恢复 LBS 功能")
    public ApiResponse<Void> unfreeze(
            @Parameter(description = "用户 ID", required = true) @PathVariable Long userId) {
        encounterService.unfreeze(userId);
        return ApiResponse.ok(null);
    }
}
