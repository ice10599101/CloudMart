package com.cloudmart.file.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.file.entity.FileAsset;
import com.cloudmart.file.service.FileAssetService;
import com.cloudmart.file.service.FileService;
import com.cloudmart.file.service.UploadQuotaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * 文件资产管理（S01/LC05）：
 *
 * <ul>
 *   <li>唯一资产上传统一走「配额预占 → 内容校验（先于落盘）→ 可见性分域存储 → 台账登记 →
 *       发布」链路；失败清理实物并归还配额；未知格式默认拒绝，MIME 以服务端嗅探为准；</li>
 *   <li>旧 /upload 与 /delete?url= 通道已删除（LC05）——旧 URL 无任何执行入口，
 *       删除统一按 fileId 归属授权 + 引用检查；</li>
 *   <li>PRIVATE 资产存储于私有域（不在 /files/** 静态映射下），不返回永久公开 URL，
 *       下载凭短期签名授权；</li>
 *   <li>内容只读取一次（嗅探/哈希/落盘复用），避免多次 getBytes。</li>
 * </ul>
 */
@RestController
@Tag(name = "文件管理", description = "文件资产上传、删除与授权下载")
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
     * S01 唯一资产上传：配额预占 → 内容校验 → 分域落盘 → 台账 → 发布；失败回滚配额并清理实物。
     */
    @PostMapping("/assets")
    @Operation(summary = "上传文件资产", description = "配额+魔数校验（SVG 拒绝）；PUBLIC 返回 /files 静态 URL，"
            + "PRIVATE 返回 fileId（无公开 URL，凭 /download-url 短期授权下载）")
    public ApiResponse<AssetUploadResponse> uploadAsset(
            @Parameter(description = "上传文件", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "可见性：PUBLIC/PRIVATE")
            @RequestParam(value = "visibility", required = false, defaultValue = "PUBLIC") String visibility,
            @Parameter(description = "用户 ID（已验签令牌主体）", hidden = true)
            @RequestHeader(value = SecurityConstants.USER_ID_HEADER, required = false) String userId,
            @Parameter(description = "管理员角色（网关注入，admin 即管理员）", hidden = true)
            @RequestHeader(value = SecurityConstants.ADMIN_ROLE_HEADER, required = false) String adminRole) {
        boolean isAdmin = ADMIN_SCOPE.equals(adminRole);
        if (!isAdmin && (userId == null || userId.isBlank())) {
            throw new BusinessException("UNAUTHORIZED", "请先登录");
        }
        if (!"PUBLIC".equalsIgnoreCase(visibility) && !"PRIVATE".equalsIgnoreCase(visibility)) {
            throw new BusinessException("FILE_VISIBILITY_INVALID", "可见性取值非法（PUBLIC/PRIVATE）");
        }
        Long ownerId = isAdmin ? null : Long.valueOf(userId);
        // 管理员豁免配额（与原配额语义一致）；普通用户预占，失败路径统一归还
        boolean quotaReserved = false;
        if (!isAdmin) {
            // Fail-Open：预占失败（Redis 异常）时放行且失败路径不归还（与配额服务语义一致）
            quotaReserved = uploadQuotaService.reserve(userId, file.getOriginalFilename());
        }
        String storageKey = null;
        try {
            if (file == null || file.isEmpty()) {
                throw new BusinessException("FILE_EMPTY", "上传文件不能为空");
            }
            // 内容只读一次：嗅探/哈希/落盘复用（S01 大文件禁止多次 getBytes 的最小可行形态）
            byte[] content;
            try {
                content = file.getBytes();
            } catch (IOException e) {
                throw new BusinessException("FILE_UPLOAD_FAILED", "读取上传内容失败");
            }
            // 校验先行（先于任何落盘）：魔数与扩展名一致，SVG 拒绝
            String mime = fileAssetService.validateContent(content, file.getOriginalFilename());
            // 可见性分域落盘（public/ 或 private/）
            storageKey = fileService.store(content, file.getOriginalFilename(), visibility);
            // 台账登记（可见性一次写定）→ 发布
            FileAsset asset = fileAssetService.persist(ownerId, file.getOriginalFilename(),
                    storageKey, file.getSize(), mime, visibility);
            String publicUrl = fileService.publicUrlOf(storageKey);
            return ApiResponse.ok(new AssetUploadResponse(
                    String.valueOf(asset.getId()), publicUrl, asset.getMime(),
                    asset.getSizeBytes(), asset.getVisibility(), asset.getStatus()));
        } catch (RuntimeException e) {
            // 失败清理：已落盘实物删除（隔离语义）+ 配额归还
            if (storageKey != null) {
                try {
                    fileService.deleteByStorageKey(storageKey);
                } catch (Exception cleanupError) {
                    org.slf4j.LoggerFactory.getLogger(FileController.class)
                            .error("上传失败清理实物异常, storageKey={}: {}", storageKey, cleanupError.getMessage());
                }
            }
            if (quotaReserved) {
                uploadQuotaService.refund(userId, file.getOriginalFilename());
            }
            throw e;
        }
    }

    /** S01：按 fileId 删除——归属授权（引用检查 + CAS READY→DELETING 关闭竞态）+ 实物回收 */
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
        fileService.deleteByStorageKey(asset.getStorageKey());
        fileAssetService.markDeleted(id);
        return ApiResponse.ok(null);
    }

    /** S01：私有附件短期下载授权（HMAC，10 分钟） */
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

    /** S01：签名授权下载（PRIVATE 附件唯一读取通道）——正确的 Content-Type、nosniff、attachment */
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

    public record AssetUploadResponse(
            @io.swagger.v3.oas.annotations.media.Schema(description = "资产ID（对外 string，19 位安全）")
            String fileId,
            @io.swagger.v3.oas.annotations.media.Schema(description = "公开访问 URL（仅 PUBLIC；PRIVATE 为 null，凭授权下载）")
            String url,
            @io.swagger.v3.oas.annotations.media.Schema(description = "实际嗅探的 MIME")
            String mime,
            @io.swagger.v3.oas.annotations.media.Schema(description = "文件大小（字节）")
            Long fileSize,
            @io.swagger.v3.oas.annotations.media.Schema(description = "可见性：PUBLIC/PRIVATE")
            String visibility,
            @io.swagger.v3.oas.annotations.media.Schema(description = "资产状态：READY")
            String status
    ) {}
}
