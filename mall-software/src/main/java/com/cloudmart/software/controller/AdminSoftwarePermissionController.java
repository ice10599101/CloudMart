package com.cloudmart.software.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.software.dto.software.AdminContactSaveRequest;
import com.cloudmart.software.dto.software.AdminGrantRequest;
import com.cloudmart.software.dto.software.AdminGrantRevokeRequest;
import com.cloudmart.software.dto.software.AdminPermissionToggleRequest;
import com.cloudmart.software.service.AdminSoftwarePermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理端资格/授权/联系入口（方案 4.0/6.14）：仅 mall-admin 代理可达；
 * 变更走 operation(ADMIN) 幂等 + software_permission_audit 同事务。
 */
@RestController
@RequestMapping("/admin/software")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INTERNAL')")
@Tag(name = "管理端-资格与授权", description = "发布资格/付款授权/联系入口")
public class AdminSoftwarePermissionController {

    private final AdminSoftwarePermissionService adminSoftwarePermissionService;

    @GetMapping("/publisher-permissions")
    @Operation(summary = "发布资格分页", description = "mall-admin 侧拼装会员资料后展示")
    public ApiResponse<Page<Map<String, Object>>> permissions(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(adminSoftwarePermissionService.permissionPage(page, size));
    }

    @PostMapping("/publisher-permissions/batch")
    @Operation(summary = "批量查询指定会员资格", description = "会员详情页用；缺失记录=false")
    public ApiResponse<List<Map<String, Object>>> permissionsOf(
            @RequestBody List<Long> userIds) {
        return ApiResponse.ok(adminSoftwarePermissionService.permissionsOf(userIds));
    }

    @PostMapping("/publisher-permissions/{userId}/toggle")
    @Operation(summary = "开通/关闭发布资格",
            description = "开通前核对会员存在且可用（失败 503）；唯一键首开幂等；权限审计")
    public ApiResponse<Boolean> togglePermission(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId,
            @PathVariable("userId") Long userId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody AdminPermissionToggleRequest request) {
        return ApiResponse.ok(adminSoftwarePermissionService.togglePermission(
                adminUserId, userId, idempotencyKey, request));
    }

    @GetMapping("/{softwareId}/access-grants")
    @Operation(summary = "付款授权列表", description = "含状态/到期/确认备注/外部核对编号")
    public ApiResponse<List<Map<String, Object>>> grants(
            @PathVariable("softwareId") Long softwareId) {
        return ApiResponse.ok(adminSoftwarePermissionService.grantsOf(softwareId));
    }

    @PostMapping("/{softwareId}/access-grants")
    @Operation(summary = "开通/延期授权",
            description = "重开更新同一行并追加审计；恰好到期即无效；会员可用性核对")
    public ApiResponse<Boolean> grant(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId,
            @PathVariable("softwareId") Long softwareId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody AdminGrantRequest request) {
        return ApiResponse.ok(adminSoftwarePermissionService.grant(
                adminUserId, softwareId, idempotencyKey, request));
    }

    @PostMapping("/{softwareId}/access-grants/{userId}/revoke")
    @Operation(summary = "撤销授权", description = "幂等；已撤销重复调用无副作用")
    public ApiResponse<Boolean> revoke(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId,
            @PathVariable("softwareId") Long softwareId,
            @PathVariable("userId") Long userId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody AdminGrantRevokeRequest request) {
        return ApiResponse.ok(adminSoftwarePermissionService.revoke(
                adminUserId, softwareId, userId, idempotencyKey, request));
    }

    @GetMapping("/access-contacts")
    @Operation(summary = "联系入口列表", description = "启用+停用全量")
    public ApiResponse<List<Map<String, Object>>> contacts() {
        return ApiResponse.ok(adminSoftwarePermissionService.contacts());
    }

    @PostMapping("/access-contacts")
    @Operation(summary = "新增/更新联系入口",
            description = "引用中仅停用不删除；HTTPS_URL 校验；CONTACT_UPDATE 审计")
    public ApiResponse<Long> saveContact(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody AdminContactSaveRequest request) {
        return ApiResponse.ok(adminSoftwarePermissionService.saveContact(
                adminUserId, idempotencyKey, request));
    }
}
