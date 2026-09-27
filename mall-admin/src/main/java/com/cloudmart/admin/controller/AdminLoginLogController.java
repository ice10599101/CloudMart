package com.cloudmart.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.admin.dto.AdminLoginLogQueryRequest;
import com.cloudmart.admin.dto.AdminLoginLogResponse;
import com.cloudmart.admin.dto.LoginLogRecordRequest;
import com.cloudmart.admin.service.AdminLoginLogService;
import com.cloudmart.common.annotation.OperLog;
import com.cloudmart.common.annotation.RequiresPermission;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.api.ApiResponse.Meta;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/logs/login")
@Tag(name = "登录日志", description = "登录日志查询与清理")
public class AdminLoginLogController {

    private final AdminLoginLogService adminLoginLogService;

    public AdminLoginLogController(AdminLoginLogService adminLoginLogService) {
        this.adminLoginLogService = adminLoginLogService;
    }

    @GetMapping("/page")
    @RequiresPermission("admin:loginlog:list")
    @Operation(summary = "分页查询登录日志", description = "支持按用户名、IP地址、状态、时间范围筛选")
    public ApiResponse<Page<AdminLoginLogResponse>> page(AdminLoginLogQueryRequest request) {
        Page<AdminLoginLogResponse> result = adminLoginLogService.page(request);
        return ApiResponse.ok(result, new Meta(
                request.page(),
                request.pageSize(),
                result.getTotal()
        ));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("admin:loginlog:remove")
    @OperLog(title = "登录日志", businessType = 3)
    @Operation(summary = "删除登录日志", description = "根据ID删除登录日志")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        adminLoginLogService.delete(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/record")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "记录登录日志", description = "内部接口：供 mall-auth 经服务令牌调用记录登录日志")
    public ApiResponse<Void> recordLogin(@Valid @RequestBody LoginLogRecordRequest request) {
        adminLoginLogService.recordLogin(
                request.username(), request.ipaddr(), request.loginLocation(),
                request.browser(), request.os(), request.status(), request.msg());
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/clean")
    @RequiresPermission("admin:loginlog:remove")
    @OperLog(title = "登录日志", businessType = 9)
    @Operation(summary = "清空登录日志", description = "清空所有登录日志")
    public ApiResponse<Void> clean() {
        adminLoginLogService.clean();
        return ApiResponse.ok(null);
    }
}
