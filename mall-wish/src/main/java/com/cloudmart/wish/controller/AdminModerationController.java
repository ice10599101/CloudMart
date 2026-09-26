package com.cloudmart.wish.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wish.entity.ModerationCase;
import com.cloudmart.wish.entity.ModerationDecision;
import com.cloudmart.wish.service.impl.ModerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 心愿治理管理端点（N01，经 mall-admin 代理，服务令牌保护）：
 * 统一待处理队列、治理决定、申诉复核。
 */
@RestController
@RequestMapping("/admin/moderation")
@Tag(name = "心愿宇宙·治理（管理）", description = "治理队列/决定/申诉复核（N01）")
@RequiredArgsConstructor
public class AdminModerationController {

    private final ModerationService moderationService;

    public record DecideRequest(
            @NotNull(message = "version 不能为空") Integer version,
            @NotBlank(message = "决定不能为空") String decision,
            String reasonCode,
            @Size(max = 500, message = "原因不能超过500字") String reasonText,
            String requestId) {
    }

    public record ResolveAppealRequest(
            @NotNull(message = "复核结论不能为空") Boolean accept,
            @Size(max = 500, message = "结论不能超过500字") String resultReason) {
    }

    @GetMapping("/cases")
    @Operation(summary = "治理队列", description = "按状态筛选的待处理/全部工单列表")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<List<ModerationCase>> cases(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        return ApiResponse.ok(moderationService.listCases(status, cursor, pageSize));
    }

    @PostMapping("/cases/{id}/decisions")
    @Operation(summary = "作出治理决定", description = "NO_ACTION/HIDE/RESTORE；HIDE/RESTORE 必填原因；"
            + "version CAS 防双审核员覆盖；决定追加写")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Long> decide(
            @Parameter(description = "工单 ID", required = true) @PathVariable("id") Long caseId,
            @Parameter(description = "操作者 ID（管理代理透传）")
            @RequestHeader(value = com.cloudmart.common.constant.SecurityConstants.USER_ID_HEADER,
                    required = false) Long actorId,
            @Valid @RequestBody DecideRequest request) {
        return ApiResponse.ok(moderationService.decide(caseId, actorId, request.version(),
                request.decision(), request.reasonCode(), request.reasonText(), request.requestId()));
    }

    @PostMapping("/appeals/{id}/decisions")
    @Operation(summary = "申诉复核", description = "复核人不得为原决定处理人；通过恢复前检查其他生效下架原因")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Void> resolveAppeal(
            @Parameter(description = "申诉 ID", required = true) @PathVariable("id") Long appealId,
            @Parameter(description = "复核人 ID（管理代理透传）")
            @RequestHeader(value = com.cloudmart.common.constant.SecurityConstants.USER_ID_HEADER,
                    required = false) Long reviewerId,
            @Valid @RequestBody ResolveAppealRequest request) {
        moderationService.resolveAppeal(appealId, reviewerId, request.accept(), request.resultReason());
        return ApiResponse.ok(null);
    }
}
