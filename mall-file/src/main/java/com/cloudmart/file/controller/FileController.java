package com.cloudmart.file.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.file.entity.FileAsset;
import com.cloudmart.file.service.FileAssetService;
import com.cloudmart.file.service.FileService;
import java.io.IOException;
import java.util.Map;
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
    private final FileAssetService fileAssetService;

    public FileController(FileService fileService, UploadQuotaService uploadQuotaService,
                          FileAssetService fileAssetService) {
        this.fileService = fileService;
        this.uploadQuotaService = uploadQuotaService;
        this.fileAssetService = fileAssetService;
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
            // FILE-01：兼容路径同样登记台账（归属取已验签主体；匿名不允许到达此处）
            byte[] content;
            try {
                content = file.getBytes();
            } catch (IOException e) {
                throw new BusinessException("FILE_UPLOAD_FAILED", "读取上传内容失败");
            }
            String storageKey = toStorageKey(url);
            if (storageKey != null) {
                fileAssetService.record(isAdmin ? null : Long.valueOf(userId),
                        file.getOriginalFilename(), storageKey, file.getSize(), content);
            }
            return ApiResponse.ok(new FileUploadResponse(url, file.getOriginalFilename(), file.getSize()));
        } catch (RuntimeException e) {
            if (reserved) {
                uploadQuotaService.refund(userId, file.getOriginalFilename());
            }
            throw e;
        }
    }

    /** FILE-01：新资产上传——返回 fileId/URL/mime/size，归属绑定已验签主体 */
    @PostMapping("/assets")
    @Operation(summary = "上传文件资产", description = "内容魔数校验（SVG 拒绝），返回 fileId/url/mime/size；"
            + "删除与私有下载均按 fileId 鉴权")
    public ApiResponse<AssetUploadResponse> uploadAsset(
            @Parameter(description = "上传文件", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "可见性：PUBLIC/PRIVATE", hidden = false)
            @RequestParam(value = "visibility", required = false, defaultValue = "PUBLIC") String visibility,
            @Parameter(description = "用户 ID（已验签令牌主体）", hidden = true)
            @RequestHeader(value = SecurityConstants.USER_ID_HEADER, required = false) String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessException("UNAUTHORIZED", "请先登录");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("FILE_EMPTY", "上传文件不能为空");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException("FILE_UPLOAD_FAILED", "读取上传内容失败");
        }
        // 复用 FileService 的存储通道落盘（扩展名/大小/配额校验在 upload 内）
        String url = fileService.upload(file);
        String storageKey = toStorageKey(url);
        FileAsset asset = fileAssetService.record(Long.valueOf(userId),
                file.getOriginalFilename(), storageKey, file.getSize(), content);
        if ("PRIVATE".equalsIgnoreCase(visibility)) {
            asset.setVisibility("PRIVATE");
            fileAssetService.updateVisibility(asset);
        }
        return ApiResponse.ok(new AssetUploadResponse(
                String.valueOf(asset.getId()), url, asset.getMime(), asset.getSizeBytes()));
    }

    /** FILE-01：按 fileId 删除——归属/管理权校验 + 引用计数（被引用 409） */
    @DeleteMapping("/assets/{id}")
    @Operation(summary = "删除文件资产", description = "仅归属者或管理员；被业务引用返回 409 FILE_REFERENCED")
    public ApiResponse<Void> deleteAsset(
            @Parameter(description = "资产ID", required = true) @PathVariable("id") Long id,
            @Parameter(description = "用户 ID（已验签令牌主体）", hidden = true)
            @RequestHeader(value = SecurityConstants.USER_ID_HEADER, required = false) String userId,
            @Parameter(description = "管理员角色（网关注入）", hidden = true)
            @RequestHeader(value = SecurityConstants.ADMIN_ROLE_HEADER, required = false) String adminRole) {
        Long requesterId = userId == null || userId.isBlank() ? null : Long.valueOf(userId);
        FileAsset asset = fileAssetService.authorizeDelete(id, requesterId, ADMIN_SCOPE.equals(adminRole));
        fileService.delete("/files/" + asset.getStorageKey());
        fileAssetService.markDeleted(id);
        return ApiResponse.ok(null);
    }

    /** FILE-01：私有附件短期下载授权（HMAC，10 分钟） */
    @GetMapping("/assets/{id}/download-url")
    @Operation(summary = "获取下载授权", description = "PRIVATE 资产仅归属者/管理员可取；返回短期签名下载路径")
    public ApiResponse<Map<String, Object>> downloadUrl(
            @Parameter(description = "资产ID", required = true) @PathVariable("id") Long id,
            @Parameter(description = "用户 ID（已验签令牌主体）", hidden = true)
            @RequestHeader(value = SecurityConstants.USER_ID_HEADER, required = false) String userId,
            @Parameter(description = "管理员角色（网关注入）", hidden = true)
            @RequestHeader(value = SecurityConstants.ADMIN_ROLE_HEADER, required = false) String adminRole) {
        Long requesterId = userId == null || userId.isBlank() ? null : Long.valueOf(userId);
        String path = fileAssetService.buildDownloadPath(id, requesterId,
                ADMIN_SCOPE.equals(adminRole), java.time.Instant.now());
        return ApiResponse.ok(Map.of("downloadPath", path, "expiresInSeconds", 600));
    }

    /** FILE-01：签名授权下载（PRIVATE 附件通道）——正确的 Content-Type、nosniff、attachment */
    @GetMapping("/assets/{id}/download")
    @Operation(summary = "签名授权下载", description = "凭短期签名 token 下载；未授权访问一律拒绝")
    public org.springframework.http.ResponseEntity<byte[]> download(
            @Parameter(description = "资产ID", required = true) @PathVariable("id") Long id,
            @RequestParam(value = "token", required = false) String token,
            @RequestParam(value = "expires", required = false) String expires) {
        fileAssetService.validateDownloadToken(id, token, expires);
        FileAsset asset = fileAssetService.requireAsset(id);
        byte[] content = fileService.readByStorageKey(asset.getStorageKey());
        if (content == null) {
            throw new BusinessException("FILE_NOT_FOUND", "文件不存在");
        }
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Type", asset.getMime())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition", "attachment; filename=\"asset-" + id + "\"")
                .body(content);
    }

    /** 将对外 URL 解析为存储键（/files/{key} → {key}）；非本地 URL 返回 null */
    private String toStorageKey(String url) {
        if (url == null) return null;
        int index = url.indexOf("/files/");
        return index < 0 ? null : url.substring(index + "/files/".length());
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除文件", description = "根据文件 URL 删除已上传的文件")
    public ApiResponse<Void> delete(
            @Parameter(description = "文件 URL", required = true)
            @RequestParam("url") String url) {
        fileService.delete(url);
        return ApiResponse.ok(null);
    }

    public record AssetUploadResponse(
            @io.swagger.v3.oas.annotations.media.Schema(description = "资产ID（对外 string，19 位安全）")
            String fileId,
            @io.swagger.v3.oas.annotations.media.Schema(description = "文件访问 URL")
            String url,
            @io.swagger.v3.oas.annotations.media.Schema(description = "实际嗅探的 MIME")
            String mime,
            @io.swagger.v3.oas.annotations.media.Schema(description = "文件大小（字节）")
            Long fileSize
    ) {}

    public record FileUploadResponse(
            @io.swagger.v3.oas.annotations.media.Schema(description = "文件访问 URL")
            String url,
            @io.swagger.v3.oas.annotations.media.Schema(description = "原始文件名")
            String originalFilename,
            @io.swagger.v3.oas.annotations.media.Schema(description = "文件大小（字节）")
            long fileSize
    ) {}
}
