package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.AdminPostRequest;
import com.cloudmart.admin.dto.AdminPostResponse;
import com.cloudmart.admin.service.AdminPostService;
import com.cloudmart.common.annotation.OperLog;
import com.cloudmart.common.annotation.RequiresPermission;
import com.cloudmart.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/posts")
@Tag(name = "岗位管理", description = "岗位CRUD")
public class AdminPostController {

    private final AdminPostService adminPostService;

    public AdminPostController(AdminPostService adminPostService) {
        this.adminPostService = adminPostService;
    }

    @GetMapping
    @RequiresPermission("admin:post:list")
    @Operation(summary = "岗位列表", description = "查询所有岗位列表")
    public ApiResponse<List<AdminPostResponse>> list() {
        return ApiResponse.ok(adminPostService.list());
    }

    @GetMapping("/{id}")
    @RequiresPermission("admin:post:query")
    @Operation(summary = "查询岗位详情", description = "根据ID获取岗位信息")
    public ApiResponse<AdminPostResponse> getById(@PathVariable("id") Long id) {
        return ApiResponse.ok(adminPostService.getById(id));
    }

    @PostMapping
    @RequiresPermission("admin:post:add")
    @OperLog(title = "岗位管理", businessType = 1)
    @Operation(summary = "新增岗位", description = "创建岗位，编码需唯一")
    public ApiResponse<Void> create(@Valid @RequestBody AdminPostRequest request) {
        adminPostService.create(request);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}")
    @RequiresPermission("admin:post:edit")
    @OperLog(title = "岗位管理", businessType = 2)
    @Operation(summary = "修改岗位", description = "更新岗位信息")
    public ApiResponse<Void> update(@PathVariable("id") Long id,
                                    @Valid @RequestBody AdminPostRequest request) {
        adminPostService.update(id, request);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("admin:post:remove")
    @OperLog(title = "岗位管理", businessType = 3)
    @Operation(summary = "删除岗位", description = "删除岗位")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        adminPostService.delete(id);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}/status")
    @RequiresPermission("admin:post:edit")
    @OperLog(title = "岗位管理", businessType = 2)
    @Operation(summary = "切换状态", description = "启用或禁用岗位")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        adminPostService.updateStatus(id, body.get("status"));
        return ApiResponse.ok(null);
    }
}
