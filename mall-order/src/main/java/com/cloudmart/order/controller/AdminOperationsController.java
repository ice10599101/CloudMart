package com.cloudmart.order.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.async.outbox.OutboxOperationsService;
import com.cloudmart.common.async.outbox.OutboxOperationsService.OutboxTaskView;
import io.swagger.v3.oas.annotations.Operation;
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
import java.util.Map;

/**
 * T16 异常处理中心（订单域）：公共 outbox_event 死信/失败任务的运营查询与受控重试。
 * mall-admin 经服务令牌代理（operations:read/retry 权限）；重试在各业务域执行，
 * 管理服务不直接写他库。重试受理 ≠ 投递成功——投递仍由 OutboxPublisher 退避执行。
 */
@RestController
@RequestMapping("/admin/operations")
@Tag(name = "内部-异常处理中心", description = "T16：订单域 outbox 失败任务查询与死信重试")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INTERNAL')")
public class AdminOperationsController {

    private final OutboxOperationsService outboxOperationsService;

    @GetMapping("/outbox")
    @Operation(summary = "失败任务分页", description = "T16：status 空=全部非 SENT；视图不含 payload（脱敏）")
    public ApiResponse<Map<String, Object>> page(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<OutboxTaskView> records = outboxOperationsService.page(status, page, size);
        return ApiResponse.ok(Map.of(
                "records", records,
                "total", records.size(),
                "stats", outboxOperationsService.stats()));
    }

    @PostMapping("/outbox/{eventId}/retry")
    @Operation(summary = "重试死信", description = "T16：仅 DEAD_LETTER 可重试（attempts 归零重新投递）；"
            + "受理≠成功，重复点击幂等")
    public ApiResponse<Map<String, Object>> retry(@PathVariable String eventId) {
        boolean accepted = outboxOperationsService.retryDeadLetter(eventId);
        return ApiResponse.ok(Map.of("eventId", eventId, "accepted", accepted));
    }
}
