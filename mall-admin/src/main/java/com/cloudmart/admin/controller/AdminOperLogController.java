package com.cloudmart.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.admin.dto.AdminOperLogQueryRequest;
import com.cloudmart.admin.dto.AdminOperLogResponse;
import com.cloudmart.admin.service.AdminOperLogService;
import com.cloudmart.common.annotation.OperLog;
import com.cloudmart.common.annotation.RequiresPermission;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.api.ApiResponse.Meta;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/logs/oper")
@Tag(name = "操作日志", description = "操作日志查询与清理")
public class AdminOperLogController {

    private final AdminOperLogService adminOperLogService;

    public AdminOperLogController(AdminOperLogService adminOperLogService) {
        this.adminOperLogService = adminOperLogService;
    }

    @GetMapping("/page")
    @RequiresPermission("admin:operlog:list")
    @Operation(summary = "分页查询操作日志", description = "支持按标题、业务类型、状态、操作人、时间范围筛选")
    public ApiResponse<Page<AdminOperLogResponse>> page(AdminOperLogQueryRequest request) {
        Page<AdminOperLogResponse> result = adminOperLogService.page(request);
        return ApiResponse.ok(result, new Meta(
                request.page(),
                request.pageSize(),
                result.getTotal()
        ));
    }

    @GetMapping("/{id}")
    @RequiresPermission("admin:operlog:query")
    @Operation(summary = "查询操作日志详情", description = "根据ID获取操作日志详情")
    public ApiResponse<AdminOperLogResponse> getById(@PathVariable("id") Long id) {
        return ApiResponse.ok(adminOperLogService.getById(id));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("admin:operlog:remove")
    @OperLog(title = "操作日志", businessType = 3)
    @Operation(summary = "删除操作日志", description = "根据ID删除操作日志")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        adminOperLogService.delete(id);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/clean")
    @RequiresPermission("admin:operlog:remove")
    @OperLog(title = "操作日志", businessType = 9)
    @Operation(summary = "清空操作日志", description = "清空所有操作日志")
    public ApiResponse<Void> clean() {
        adminOperLogService.clean();
        return ApiResponse.ok(null);
    }
}
