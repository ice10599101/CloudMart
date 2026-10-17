package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.AdminFileAssetPage;
import com.cloudmart.admin.dto.AdminFileAssetRow;
import com.cloudmart.admin.feign.FileFeignClient;
import com.cloudmart.common.annotation.OperLog;
import com.cloudmart.common.annotation.RequiresPermission;
import com.cloudmart.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADMIN-04：管理员文件资产管理（代理 mall-file 管理员域）。
 *
 * <p>只读写 owner_type=ADMIN 的运营资产；后台不能浏览用户上传/私聊/售后私有内容。
 * 删除复用 mall-file 的引用保护与 CAS 状态流转，被业务引用的文件返回 409。
 * 上传统一走 /file/assets（管理员令牌域 + purpose 必填），见 mall-file FileController。</p>
 */
@RestController
@RequestMapping("/file/assets")
@Tag(name = "文件资产管理", description = "管理员域资产分页/详情/删除")
@RequiredArgsConstructor
public class AdminFileController {

    private final FileFeignClient fileFeignClient;

    @GetMapping
    @RequiresPermission("system:file:list")
    @Operation(summary = "管理员域资产分页", description = "仅 owner_type=ADMIN；status/purpose 过滤可选；上限 100")
    public ApiResponse<AdminFileAssetPage> pageAssets(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @Parameter(description = "状态过滤：READY/DELETING/DELETED") @RequestParam(required = false) String status,
            @Parameter(description = "业务用途过滤") @RequestParam(required = false) String purpose) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        return fileFeignClient.pageAdminAssets(safePage, safeSize, status, purpose);
    }

    @GetMapping("/{fileId}")
    @RequiresPermission("system:file:list")
    @Operation(summary = "管理员域资产详情", description = "非 ADMIN 域资产返回 404")
    public ApiResponse<AdminFileAssetRow> detail(@PathVariable Long fileId) {
        return fileFeignClient.adminAsset(fileId);
    }

    @DeleteMapping("/{fileId}")
    @OperLog(title = "文件资产管理", businessType = 3)
    @RequiresPermission("system:file:delete")
    @Operation(summary = "删除管理员域资产", description = "引用保护：被业务引用返回 409 FILE_REFERENCED")
    public ApiResponse<Void> delete(@PathVariable Long fileId) {
        return fileFeignClient.deleteAdminAsset(fileId);
    }
}
