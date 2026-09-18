package com.cloudmart.file.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.file.service.FileService;
import com.cloudmart.file.service.UploadQuotaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Tag(name = "文件管理", description = "文件上传、删除与预览")
public class FileController {

    private static final String ADMIN_SCOPE = "admin";

    private final FileService fileService;
    private final UploadQuotaService uploadQuotaService;

    public FileController(FileService fileService, UploadQuotaService uploadQuotaService) {
        this.fileService = fileService;
        this.uploadQuotaService = uploadQuotaService;
    }

    /**
     * 上传配额规则：每用户每日图片 30 张、其他类型合计 15 次；管理员豁免。
     * 匿名请求直接拒绝——网关对已认证请求才注入 X-User-Id，此处缺失即未登录。
     */
    @PostMapping("/upload")
    @Operation(summary = "上传文件", description = "上传文件到服务器本地存储，返回访问 URL；受每日上传配额限制（图片 30/日，其他 15/日）")
    public ApiResponse<FileUploadResponse> upload(
            @Parameter(description = "上传文件", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "用户 ID（网关注入）", hidden = true)
            @RequestHeader(value = SecurityConstants.USER_ID_HEADER, required = false) String userId,
            @Parameter(description = "管理员角色（网关注入，admin 即管理员）", hidden = true)
            @RequestHeader(value = SecurityConstants.ADMIN_ROLE_HEADER, required = false) String adminRole) {
        boolean isAdmin = ADMIN_SCOPE.equals(adminRole);
        if (!isAdmin && (userId == null || userId.isBlank())) {
            throw new BusinessException("UNAUTHORIZED", "请先登录");
        }
        boolean reserved = false;
        if (!isAdmin) {
            reserved = uploadQuotaService.reserve(userId, file.getOriginalFilename());
        }
        try {
            String url = fileService.upload(file);
            return ApiResponse.ok(new FileUploadResponse(url, file.getOriginalFilename(), file.getSize()));
        } catch (RuntimeException e) {
            if (reserved) {
                uploadQuotaService.refund(userId, file.getOriginalFilename());
            }
            throw e;
        }
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除文件", description = "根据文件 URL 删除已上传的文件")
    public ApiResponse<Void> delete(
            @Parameter(description = "文件 URL", required = true)
            @RequestParam("url") String url) {
        fileService.delete(url);
        return ApiResponse.ok(null);
    }

    public record FileUploadResponse(
            @io.swagger.v3.oas.annotations.media.Schema(description = "文件访问 URL")
            String url,
            @io.swagger.v3.oas.annotations.media.Schema(description = "原始文件名")
            String originalFilename,
            @io.swagger.v3.oas.annotations.media.Schema(description = "文件大小（字节）")
            long fileSize
    ) {}
}
