package com.cloudmart.job.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cloudmart.common.annotation.OperLog;
import com.cloudmart.common.annotation.RequiresPermission;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.job.dto.SysJobLogResponse;
import com.cloudmart.job.dto.SysJobRequest;
import com.cloudmart.job.dto.SysJobResponse;
import com.cloudmart.job.service.SysJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@Tag(name = "定时任务", description = "定时任务管理与执行日志")
public class SysJobController {

    private final SysJobService sysJobService;

    public SysJobController(SysJobService sysJobService) {
        this.sysJobService = sysJobService;
    }

    @GetMapping("/list")
    @RequiresPermission("monitor:job:list")
    @Operation(summary = "任务列表", description = "分页查询定时任务")
    public ApiResponse<IPage<SysJobResponse>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String jobName,
            @RequestParam(required = false) Integer status) {
        IPage<SysJobResponse> result = sysJobService.page(page, pageSize, jobName, status);
        return ApiResponse.ok(result, new ApiResponse.Meta(page, pageSize, result.getTotal()));
    }

    @GetMapping("/{id}")
    @RequiresPermission("monitor:job:query")
    @Operation(summary = "任务详情", description = "查询定时任务详情")
    public ApiResponse<SysJobResponse> getInfo(@PathVariable Long id) {
        return ApiResponse.ok(sysJobService.getById(id));
    }

    @PostMapping
    @RequiresPermission("monitor:job:add")
    @OperLog(title = "定时任务", businessType = 1)
    @Operation(summary = "新增任务", description = "创建定时任务")
    public ApiResponse<Long> add(@Valid @RequestBody SysJobRequest request) {
        return ApiResponse.ok(sysJobService.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission("monitor:job:edit")
    @OperLog(title = "定时任务", businessType = 2)
    @Operation(summary = "修改任务", description = "更新定时任务")
    public ApiResponse<Void> edit(@PathVariable Long id, @Valid @RequestBody SysJobRequest request) {
        sysJobService.update(id, request);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("monitor:job:remove")
    @OperLog(title = "定时任务", businessType = 3)
    @Operation(summary = "删除任务", description = "删除定时任务")
    public ApiResponse<Void> remove(@PathVariable Long id) {
        sysJobService.delete(id);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}/status")
    @RequiresPermission("monitor:job:changeStatus")
    @OperLog(title = "定时任务", businessType = 2)
    @Operation(summary = "切换状态", description = "暂停/恢复定时任务")
    public ApiResponse<Void> changeStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        sysJobService.changeStatus(id, body.get("status"));
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}/run")
    @RequiresPermission("monitor:job:changeStatus")
    @OperLog(title = "定时任务", businessType = 0)
    @Operation(summary = "立即执行", description = "立即执行一次定时任务")
    public ApiResponse<Void> run(@PathVariable Long id) {
        sysJobService.runOnce(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/log/page")
    @RequiresPermission("monitor:job:list")
    @Operation(summary = "任务日志", description = "分页查询任务执行日志")
    public ApiResponse<IPage<SysJobLogResponse>> pageJobLogs(
            @RequestParam(required = false) Long jobId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        IPage<SysJobLogResponse> result = sysJobService.pageJobLogs(jobId, page, pageSize);
        return ApiResponse.ok(result, new ApiResponse.Meta(page, pageSize, result.getTotal()));
    }

    @DeleteMapping("/log/{id}")
    @RequiresPermission("monitor:job:remove")
    @OperLog(title = "任务日志", businessType = 3)
    @Operation(summary = "删除日志", description = "删除任务执行日志")
    public ApiResponse<Void> deleteLog(@PathVariable Long id) {
        sysJobService.deleteJobLog(id);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/log/clean")
    @RequiresPermission("monitor:job:remove")
    @OperLog(title = "任务日志", businessType = 3)
    @Operation(summary = "清空日志", description = "清空所有任务执行日志")
    public ApiResponse<Void> cleanLog() {
        sysJobService.cleanJobLogs();
        return ApiResponse.ok(null);
    }
}
