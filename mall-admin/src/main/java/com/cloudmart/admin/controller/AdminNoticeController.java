package com.cloudmart.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.admin.dto.AdminNoticeRequest;
import com.cloudmart.admin.dto.AdminNoticeResponse;
import com.cloudmart.admin.service.AdminNoticeService;
import com.cloudmart.common.annotation.OperLog;
import com.cloudmart.common.annotation.RequiresPermission;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.api.ApiResponse.Meta;
import com.cloudmart.common.context.AdminSecurityContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notices")
@Tag(name = "通知公告", description = "通知公告CRUD与已读管理")
public class AdminNoticeController {

    private final AdminNoticeService adminNoticeService;

    public AdminNoticeController(AdminNoticeService adminNoticeService) {
        this.adminNoticeService = adminNoticeService;
    }

    @GetMapping("/page")
    @RequiresPermission("admin:notice:list")
    @Operation(summary = "分页查询通知公告", description = "支持按标题、类型筛选")
    public ApiResponse<Page<AdminNoticeResponse>> page(
            @RequestParam(required = false) String noticeTitle,
            @RequestParam(required = false) Integer noticeType,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        Page<AdminNoticeResponse> result = adminNoticeService.page(noticeTitle, noticeType, page, pageSize);
        return ApiResponse.ok(result, new Meta(page, pageSize, result.getTotal()));
    }

    @GetMapping("/{id}")
    @RequiresPermission("admin:notice:query")
    @Operation(summary = "查询通知公告详情", description = "根据ID获取通知公告详情含阅读数")
    public ApiResponse<AdminNoticeResponse> getById(@PathVariable("id") Long id) {
        return ApiResponse.ok(adminNoticeService.getById(id));
    }

    @PostMapping
    @RequiresPermission("admin:notice:add")
    @OperLog(title = "通知公告", businessType = 1)
    @Operation(summary = "新增通知公告", description = "创建通知公告")
    public ApiResponse<Void> create(@Valid @RequestBody AdminNoticeRequest request) {
        adminNoticeService.create(request);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}")
    @RequiresPermission("admin:notice:edit")
    @OperLog(title = "通知公告", businessType = 2)
    @Operation(summary = "修改通知公告", description = "更新通知公告信息")
    public ApiResponse<Void> update(@PathVariable("id") Long id,
                                    @Valid @RequestBody AdminNoticeRequest request) {
        adminNoticeService.update(id, request);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("admin:notice:remove")
    @OperLog(title = "通知公告", businessType = 3)
    @Operation(summary = "删除通知公告", description = "删除通知公告")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        adminNoticeService.delete(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "标记通知已读", description = "当前登录用户标记通知公告为已读")
    public ApiResponse<Void> markAsRead(@PathVariable("id") Long noticeId) {
        Long userId = AdminSecurityContext.get().userId();
        adminNoticeService.markAsRead(noticeId, userId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/unread")
    @Operation(summary = "未读通知列表", description = "获取当前登录用户的未读通知公告")
    public ApiResponse<List<AdminNoticeResponse>> unreadList() {
        Long userId = AdminSecurityContext.get().userId();
        return ApiResponse.ok(adminNoticeService.unreadList(userId));
    }

    @PutMapping("/{id}/status")
    @RequiresPermission("system:notice:edit")
    @OperLog(title = "通知公告", businessType = 2)
    @Operation(summary = "切换状态", description = "启用或禁用通知公告")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        adminNoticeService.updateStatus(id, body.get("status"));
        return ApiResponse.ok(null);
    }
}
