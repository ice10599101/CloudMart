package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.AdminConfigRequest;
import com.cloudmart.admin.dto.AdminConfigResponse;
import com.cloudmart.admin.service.AdminConfigService;
import com.cloudmart.common.annotation.OperLog;
import com.cloudmart.common.annotation.RequiresPermission;
import com.cloudmart.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/configs")
@Tag(name = "参数设置", description = "系统参数配置CRUD与缓存刷新")
public class AdminConfigController {

    private final AdminConfigService adminConfigService;

    public AdminConfigController(AdminConfigService adminConfigService) {
        this.adminConfigService = adminConfigService;
    }

    @GetMapping
    @RequiresPermission("admin:config:list")
    @Operation(summary = "参数配置列表", description = "查询所有参数配置列表")
    public ApiResponse<List<AdminConfigResponse>> list() {
        return ApiResponse.ok(adminConfigService.list());
    }

    @GetMapping("/{id}")
    @RequiresPermission("admin:config:query")
    @Operation(summary = "查询参数配置详情", description = "根据ID获取参数配置信息")
    public ApiResponse<AdminConfigResponse> getById(@PathVariable("id") Long id) {
        return ApiResponse.ok(adminConfigService.getById(id));
    }

    @GetMapping("/key/{configKey}")
    @RequiresPermission("admin:config:query")
    @Operation(summary = "根据键名查询配置", description = "根据configKey获取参数配置信息")
    public ApiResponse<AdminConfigResponse> getByKey(@PathVariable("configKey") String configKey) {
        return ApiResponse.ok(adminConfigService.getByKey(configKey));
    }

    @PostMapping
    @RequiresPermission("admin:config:add")
    @OperLog(title = "参数设置", businessType = 1)
    @Operation(summary = "新增参数配置", description = "创建参数配置，键名需唯一")
    public ApiResponse<Void> create(@Valid @RequestBody AdminConfigRequest request) {
        adminConfigService.create(request);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}")
    @RequiresPermission("admin:config:edit")
    @OperLog(title = "参数设置", businessType = 2)
    @Operation(summary = "修改参数配置", description = "更新参数配置信息")
    public ApiResponse<Void> update(@PathVariable("id") Long id,
                                    @Valid @RequestBody AdminConfigRequest request) {
        adminConfigService.update(id, request);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("admin:config:remove")
    @OperLog(title = "参数设置", businessType = 3)
    @Operation(summary = "删除参数配置", description = "删除参数配置")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        adminConfigService.delete(id);
        return ApiResponse.ok(null);
    }

    @PutMapping("/cache/refresh")
    @RequiresPermission("admin:config:remove")
    @OperLog(title = "参数设置", businessType = 9)
    @Operation(summary = "刷新参数配置缓存", description = "重新加载所有参数配置到Redis缓存")
    public ApiResponse<Void> refreshCache() {
        adminConfigService.refreshCache();
        return ApiResponse.ok(null);
    }
}
