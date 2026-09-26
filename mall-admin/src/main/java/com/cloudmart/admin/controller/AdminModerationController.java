package com.cloudmart.admin.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.annotation.RequiresPermission;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.admin.feign.ModerationFeignClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 心愿治理工作台代理（N01）：转发 mall-wish /admin/moderation/**。
 * 权限码与任务书 §8.1 对齐：business:wishModeration:list/audit、business:wishAppeal:review。
 */
@RestController
@RequestMapping("/wish/moderation")
@Tag(name = "心愿治理工作台", description = "举报/工单/申诉（N01）")
@RequiredArgsConstructor
@Validated
public class AdminModerationController {

    private final ModerationFeignClient moderationFeignClient;

    public record DecideRequest(
            @NotNull(message = "version 不能为空") Integer version,
            @NotBlank(message = "决定不能为空") String decision,
            String reasonCode,
            @Size(max = 500) String reasonText,
            String requestId) {
    }

    public record ResolveAppealRequest(
            @NotNull(message = "复核结论不能为空") Boolean accept,
            @Size(max = 500) String resultReason) {
    }

    @GetMapping("/cases")
    @RequiresPermission("business:wishModeration:list")
    @Operation(summary = "治理队列", description = "按状态筛选（OPEN/IN_REVIEW/RESOLVED）")
    public ApiResponse<List<Map<String, Object>>> cases(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        return moderationFeignClient.listCases(status, cursor, pageSize);
    }

    @PostMapping("/cases/{id}/decisions")
    @RequiresPermission("business:wishModeration:audit")
    @Operation(summary = "作出治理决定", description = "HIDE/RESTORE 必填原因；操作者经透传头落审计")
    public ApiResponse<Long> decide(
            @Parameter(description = "工单 ID", required = true) @PathVariable("id") Long caseId,
            @Parameter(description = "操作者 ID（网关注入）")
            @RequestHeader(value = SecurityConstants.USER_ID_HEADER, required = false) Long actorId,
            @Valid @RequestBody DecideRequest request) {
        return moderationFeignClient.decide(caseId, Map.of(
                "version", request.version(),
                "decision", request.decision(),
                "reasonCode", request.reasonCode() == null ? "" : request.reasonCode(),
                "reasonText", request.reasonText() == null ? "" : request.reasonText(),
                "requestId", request.requestId() == null ? "" : request.requestId()), actorId);
    }

    @PostMapping("/appeals/{id}/decisions")
    @RequiresPermission("business:wishAppeal:review")
    @Operation(summary = "申诉复核", description = "复核人不得为原决定处理人（服务端强制）")
    public ApiResponse<Void> resolveAppeal(
            @Parameter(description = "申诉 ID", required = true) @PathVariable("id") Long appealId,
            @Parameter(description = "复核人 ID（网关注入）")
            @RequestHeader(value = SecurityConstants.USER_ID_HEADER, required = false) Long reviewerId,
            @Valid @RequestBody ResolveAppealRequest request) {
        return moderationFeignClient.resolveAppeal(appealId, Map.of(
                "accept", request.accept(),
                "resultReason", request.resultReason() == null ? "" : request.resultReason()), reviewerId);
    }
}
