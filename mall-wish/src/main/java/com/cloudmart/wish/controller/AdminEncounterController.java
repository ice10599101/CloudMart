package com.cloudmart.wish.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台-擦肩而过风控 Controller—— **产品已下线**（2026-10 产品决策，
 * 随附近模式/轨迹上报一并下线：无轨迹即无可疑跳跃/冻结对象）。
 *
 * <p>端点显式 410（{@code WISH_FEATURE_OFFLINE}）；历史冻结记录（LBS 表）
 * 保留未删，产品恢复时回退本提交即可。</p>
 */
@RestController
@RequestMapping("/admin/encounter")
@PreAuthorize("hasRole('INTERNAL')")
@Tag(name = "管理后台-擦肩而过风控", description = "产品已下线；端点保留路由语义，统一 410")
public class AdminEncounterController {

    private static final BusinessException OFFLINE =
            new BusinessException("WISH_FEATURE_OFFLINE", "擦肩而过功能已下线（信笺玩法已由漂流瓶替代）");

    @GetMapping("/suspicious")
    @Operation(summary = "可疑跳跃记录（已下线）")
    public ApiResponse<Void> listSuspicious() {
        throw OFFLINE;
    }

    @GetMapping("/freezes")
    @Operation(summary = "冻结用户列表（已下线）")
    public ApiResponse<Void> listFreezes() {
        throw OFFLINE;
    }

    @PostMapping("/freezes/{userId}/unfreeze")
    @Operation(summary = "解冻（已下线）")
    public ApiResponse<Void> unfreeze(@PathVariable Long userId) {
        throw OFFLINE;
    }
}
