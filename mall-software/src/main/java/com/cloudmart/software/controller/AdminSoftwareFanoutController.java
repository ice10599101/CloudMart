package com.cloudmart.software.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.software.entity.SoftwareFanoutTask;
import com.cloudmart.software.repository.SoftwareFanoutTaskMapper;
import com.cloudmart.software.repository.SoftwareSubscriptionDeliveryMapper;
import com.cloudmart.software.service.SoftwareAuditService;
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

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端-通知与检查任务运维（SW10）：
 * fanout 任务状态、投递聚合（目标/成功/取消/取代/失败）、游标、失败原因、
 * 下次执行时间与人工重试（重试沿用原 eventId/幂等键，不生成新的发布通知）。
 * 权限：software:fanout（代理层裁决），本服务只验内部令牌。
 */
@RestController
@RequestMapping("/admin/software/fanout")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INTERNAL')")
@Tag(name = "管理端-通知任务", description = "订阅通知 fanout 任务运维（SW10）")
public class AdminSoftwareFanoutController {

    private final SoftwareFanoutTaskMapper fanoutTaskMapper;
    private final SoftwareSubscriptionDeliveryMapper deliveryMapper;
    private final SoftwareAuditService auditService;
    private final Clock clock;

    @GetMapping("/tasks")
    @Operation(summary = "通知任务列表", description = "状态/游标/失败原因/下次执行时间/投递聚合")
    public ApiResponse<Map<String, Object>> tasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<SoftwareFanoutTask> result = fanoutTaskMapper.selectPage(
                Page.of(Math.max(page, 1), Math.min(Math.max(size, 1), 100)),
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SoftwareFanoutTask>()
                        .orderByDesc(SoftwareFanoutTask::getId));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SoftwareFanoutTask task : result.getRecords()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("taskId", String.valueOf(task.getId()));
            row.put("softwareId", String.valueOf(task.getSoftwareId()));
            row.put("revisionId", String.valueOf(task.getRevisionId()));
            row.put("publicationSeq", String.valueOf(task.getPublicationSeq()));
            row.put("status", task.getStatus());
            row.put("cursorSubscriptionId", String.valueOf(task.getCursorSubscriptionId()));
            row.put("attempts", String.valueOf(task.getAttempts() == null ? 0 : task.getAttempts()));
            row.put("nextRetryAt", task.getNextRetryAt() == null ? null
                    : String.valueOf(task.getNextRetryAt()));
            row.put("lastError", task.getLastError());
            row.put("deliveryCounts", deliveryCounts(task));
            rows.add(row);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("current", result.getCurrent());
        data.put("size", result.getSize());
        data.put("total", result.getTotal());
        data.put("records", rows);
        return ApiResponse.ok(data);
    }

    @PostMapping("/tasks/{taskId}/retry")
    @Operation(summary = "人工重试", description = "重新入队；沿用原发布事件身份（seq/revisionId 不变）")
    public ApiResponse<Boolean> retry(@PathVariable("taskId") Long taskId) {
        SoftwareFanoutTask task = fanoutTaskMapper.selectById(taskId);
        if (task == null) {
            return ApiResponse.fail("SOFTWARE_NOT_FOUND", "通知任务不存在");
        }
        int updated = fanoutTaskMapper.manualRetry(taskId, LocalDateTime.now(clock));
        if (updated == 0) {
            return ApiResponse.fail("SOFTWARE_STATE_CONFLICT", "任务当前状态不可重试（可能已完成）");
        }
        auditService.record(task.getSoftwareId(), task.getRevisionId(), adminId(), "ADMIN",
                "FANOUT_RETRY", task.getStatus(), "PENDING",
                "人工重试通知任务 seq=" + task.getPublicationSeq(), null);
        return ApiResponse.ok(true);
    }

    private Map<String, Object> deliveryCounts(SoftwareFanoutTask task) {
        Map<String, Object> counts = new LinkedHashMap<>();
        for (Map<String, Object> row : deliveryMapper.countByStatusGroup(
                task.getSoftwareId(), task.getPublicationSeq())) {
            counts.put(String.valueOf(row.get("status")), String.valueOf(row.get("cnt")));
        }
        return counts;
    }

    private Long adminId() {
        // 审计留痕：代理层已验管理员身份；这里以 0 表示系统运维动作主体
        return 0L;
    }
}
