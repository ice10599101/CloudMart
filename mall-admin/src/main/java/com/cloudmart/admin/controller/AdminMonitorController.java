package com.cloudmart.admin.controller;

import com.cloudmart.admin.service.AdminMonitorService;
import com.cloudmart.common.annotation.RequiresPermission;
import com.cloudmart.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/monitor")
@Tag(name = "服务监控", description = "服务器和缓存监控信息")
public class AdminMonitorController {

    private final AdminMonitorService adminMonitorService;

    public AdminMonitorController(AdminMonitorService adminMonitorService) {
        this.adminMonitorService = adminMonitorService;
    }

    @GetMapping("/server")
    @RequiresPermission("monitor:server:list")
    @Operation(summary = "服务器信息", description = "获取 JVM、CPU、内存、磁盘等服务器信息")
    public ApiResponse<Map<String, Object>> serverInfo() {
        return ApiResponse.ok(adminMonitorService.getServerInfo());
    }

    @GetMapping("/cache")
    @RequiresPermission("monitor:cache:list")
    @Operation(summary = "缓存信息", description = "获取 Redis 缓存信息")
    public ApiResponse<Map<String, Object>> cacheInfo() {
        return ApiResponse.ok(adminMonitorService.getCacheInfo());
    }
}
