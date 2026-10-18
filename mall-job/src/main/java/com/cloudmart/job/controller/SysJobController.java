package com.cloudmart.job.controller;

import com.cloudmart.common.annotation.OperLog;
import com.cloudmart.common.annotation.RequiresPermission;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.job.service.impl.XxlJobAdapterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * OPS-01：任务管理适配层——展示 XXL-JOB 实际配置与执行日志（只读事实），
 * 手动运行走白名单 handler。生命周期（新建/编辑/启停/Cron）一律走 XXL
 * 调度中心控制台，本服务不再维护第二套 Cron 注册表。
 */
@RestController
@Tag(name = "定时任务(XXL 适配)", description = "OPS-01：XXL-JOB 事实只读适配 + 白名单手动运行")
public class SysJobController {

    private final XxlJobAdapterService adapterService;

    public SysJobController(XxlJobAdapterService adapterService) {
        this.adapterService = adapterService;
    }

    @GetMapping("/list")
    @RequiresPermission("monitor:job:list")
    @Operation(summary = "任务列表", description = "XXL 事实：handler/Cron/启停状态/负责人（只读适配）")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String jobName) {
        return ApiResponse.ok(adapterService.page(page, pageSize, jobName));
    }

    @GetMapping("/{id}")
    @RequiresPermission("monitor:job:list")
    @Operation(summary = "任务详情")
    public ApiResponse<Map<String, Object>> getById(@PathVariable long id) {
        return ApiResponse.ok(adapterService.getById(id));
    }

    @PostMapping("/{id}/run")
    @OperLog(title = "定时任务", businessType = 2)
    @RequiresPermission("monitor:job:run")
    @Operation(summary = "手动运行", description = "白名单 handler 直接执行（与计划触发同一业务动作与幂等事实）")
    public ApiResponse<Void> runOnce(@PathVariable long id) {
        adapterService.runOnce(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/log/page")
    @RequiresPermission("monitor:job:list")
    @Operation(summary = "执行日志", description = "XXL 事实：触发/执行结果码与消息（handle_code 200=成功）")
    public ApiResponse<Map<String, Object>> pageLogs(
            @RequestParam long jobId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return ApiResponse.ok(adapterService.pageLogs(jobId, page, pageSize));
    }
}
