package com.cloudmart.software.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.software.service.SoftwareSourcePatrolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * SF07 来源健康巡检端点。
 * 内部触发端点：调度与运行日志由现有任务设施托管（mall-job/xxl-job），
 * 本服务只提供业务入口；管理员手动重检经 mall-admin 代理（software:link）。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "来源健康巡检", description = "SF07 巡检触发与手动重检")
public class SoftwareSourcePatrolController {

    private final SoftwareSourcePatrolService patrolService;

    /** 内部触发：一轮巡检（预算内挑选最久未检的 HTTPS 来源） */
    @org.springframework.web.bind.annotation.PostMapping("/internal/source-patrol/run")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "巡检一轮", description = "仅内部调度可达；预算内挑选最久未检来源；不覆盖人工结论")
    public ApiResponse<Integer> run() {
        return ApiResponse.ok(patrolService.runOnce());
    }

    /** 管理员手动重检（mall-admin 代理；software:link） */
    @org.springframework.web.bind.annotation.PostMapping(
            "/admin/software/sources/{sourceId}/patrol")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "手动重检来源", description = "立即探测单条 HTTPS 来源；不覆盖人工状态；磁力/种子不支持")
    public ApiResponse<Map<String, Object>> patrol(
            @PathVariable("sourceId") Long sourceId) {
        return ApiResponse.ok(patrolService.patrolById(sourceId));
    }
}
