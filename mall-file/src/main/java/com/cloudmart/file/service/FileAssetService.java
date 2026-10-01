package com.cloudmart.file.service;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.file.entity.FileAsset;
import com.cloudmart.file.repository.FileAssetMapper;
import com.cloudmart.file.repository.FileReferenceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/**
 * 文件资产台账（FILE-01）：归属、内容校验、引用计数与短期下载授权。
 *
 * <ul>
 *   <li>登记：所有上传（含旧 /upload 兼容路径）写入 file_asset，归属取自
 *       已验签令牌主体（X-User-Id 由 SEC-01 过滤器强制改写，不可伪造）；</li>
 *   <li>内容校验：扩展名声明必须与魔数嗅探结果一致；SVG 默认拒绝（可携带脚本）；</li>
 *   <li>删除：按 fileId 校验归属（无主资产仅管理员），被业务引用返回 409，
 *       绝不允许以任意 URL 决定删除对象；</li>
 *   <li>私有附件：下载凭 HMAC 短期签名 URL（默认 10 分钟），非归属者不可取。</li>
 * </ul>
 */
@Slf4j
@Service
public class FileAssetService {

    private final FileAssetMapper fileAssetMapper;
    private final FileReferenceMapper fileReferenceMapper;
    private final byte[] signingSecret;

    public FileAssetService(FileAssetMapper fileAssetMapper,
                            FileReferenceMapper fileReferenceMapper,
                            @Value("${file.download-signing-secret:${CLOUDMART_FILE_SIGNING_SECRET:}}") String signingSecret) {
        this.fileAssetMapper = fileAssetMapper;
        this.fileReferenceMapper = fileReferenceMapper;
        this.signingSecret = signingSecret == null || signingSecret.isBlank()
                ? new byte[0] : signingSecret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * S01 内容校验（先于落盘执行）：魔数嗅探实际类型并核对扩展名声明；SVG 一律拒绝。
     *
     * @return 嗅探得到的 MIME
     */
    public String validateContent(byte[] content, String originalName) {
        String sniffedMime = sniffMime(content);
        String declaredExtension = extensionOf(originalName);
        if (!sniffedMimeAllowed(sniffedMime, declaredExtension)) {
            throw new BusinessException("FILE_TYPE_NOT_ALLOWED",
                    "文件内容与扩展名不符或不支持的类型: " + declaredExtension);
        }
        return sniffedMime;
    }

    /** 登记上传结果（校验通过、物理文件已按可见性分域落盘后调用；可见性一次写定） */
    public FileAsset persist(Long ownerId, String originalName, String storageKey, long sizeBytes,
                             String mime, String sha256, String visibility) {
        FileAsset asset = new FileAsset();
        asset.setOwnerId(ownerId);
        asset.setOriginalName(sanitizeOriginalName(originalName));
        asset.setStorageKey(storageKey);
        asset.setMime(mime);
        asset.setSizeBytes(sizeBytes);
        asset.setSha256(sha256);
        asset.setVisibility("PRIVATE".equalsIgnoreCase(visibility) ? "PRIVATE" : "PUBLIC");
        asset.setStatus("READY");
        fileAssetMapper.insert(asset);
        return asset;
    }

    /** 内容指纹（sha256 列 NOT NULL；内容已由控制器单次读取复用） */
    public String sha256Hex(byte[] content) {
        return sha256HexOf(content);
    }

    /**
     * 按资产 ID 删除：归属者或管理员；被业务引用返回 409；无主资产仅管理员。
     * 物理文件删除由调用方（FileService）按 storageKey 执行。
     */
    public FileAsset authorizeDelete(Long fileId, Long requesterId, boolean isAdmin) {
        FileAsset asset = fileAssetMapper.selectById(fileId);
        if (asset == null || "DELETED".equals(asset.getStatus()) || "DELETING".equals(asset.getStatus())) {
            throw new BusinessException("FILE_NOT_FOUND", "文件不存在");
        }
        boolean owner = asset.getOwnerId() != null && asset.getOwnerId().equals(requesterId);
        boolean unclaimed = asset.getOwnerId() == null;
        if (unclaimed ? !isAdmin : !owner && !isAdmin) {
            throw new BusinessException("FILE_FORBIDDEN", "无权删除该文件");
        }
        Long references = fileReferenceMapper.selectCount(
                new LambdaQueryWrapper<com.cloudmart.file.entity.FileReference>()
                        .eq(com.cloudmart.file.entity.FileReference::getAssetId, fileId));
        if (references != null && references > 0) {
            throw new BusinessException("FILE_REFERENCED", "文件仍被 " + references + " 处业务引用，无法删除");
        }
        // S01 引用登记协议：CAS READY → DELETING——引用登记方只能对 READY 资产建档，
        // "查引用后、删除前"新增引用的竞争窗口由此关闭；0 行 = 并发删除/状态已推进
        int marked = fileAssetMapper.markDeleting(fileId);
        if (marked == 0) {
            throw new BusinessException("FILE_DELETE_CONFLICT", "文件删除状态已变更，请刷新重试");
        }
        asset.setStatus("DELETING");
        return asset;
    }

    /** 私有附件下载授权：仅归属者（管理员可审计）；返回短期签名下载路径 */
    public String buildDownloadPath(Long fileId, Long requesterId, boolean isAdmin, Instant now) {
        FileAsset asset = requireAsset(fileId);
        boolean owner = asset.getOwnerId() != null && asset.getOwnerId().equals(requesterId);
        if ("PRIVATE".equals(asset.getVisibility()) && !owner && !isAdmin) {
            throw new BusinessException("FILE_FORBIDDEN", "无权访问该文件");
        }
        long expiresAt = now.plusSeconds(600).getEpochSecond();
        String token = sign(fileId, expiresAt);
        return "/file/assets/" + fileId + "/download?token=" + token + "&expires=" + expiresAt;
    }

    /** 校验下载签名（静态资源通道之外的一次性授权下载）；签名密钥未配置时拒绝（fail-closed） */
    public void validateDownloadToken(Long fileId, String token, String expires) {
        if (signingSecret.length == 0) {
            throw new BusinessException("FILE_FORBIDDEN", "下载签名未配置，拒绝访问");
        }
        if (token == null || expires == null) {
            throw new BusinessException("FILE_FORBIDDEN", "缺少下载授权");
        }
        long expiresAt;
        try {
            expiresAt = Long.parseLong(expires);
        } catch (NumberFormatException e) {
            throw new BusinessException("FILE_FORBIDDEN", "非法的下载授权");
        }
        if (Instant.now().getEpochSecond() > expiresAt) {
            throw new BusinessException("FILE_FORBIDDEN", "下载授权已过期");
        }
        if (!MessageDigest.isEqual(sign(fileId, expiresAt).getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException("FILE_FORBIDDEN", "下载授权无效");
        }
    }

    /** 修改可见性（PUBLIC/PRIVATE） */
    public void updateVisibility(FileAsset asset) {
        fileAssetMapper.updateById(asset);
    }

    /** 删除后标记台账状态（保留审计记录，不物理删除台账行） */
    public void markDeleted(Long fileId) {
        FileAsset asset = fileAssetMapper.selectById(fileId);
        if (asset != null) {
            asset.setStatus("DELETED");
            fileAssetMapper.updateById(asset);
        }
    }

    public FileAsset requireAsset(Long fileId) {
        FileAsset asset = fileAssetMapper.selectById(fileId);
        if (asset == null || "DELETED".equals(asset.getStatus())) {
            throw new BusinessException("FILE_NOT_FOUND", "文件不存在");
        }
        return asset;
    }

    private String sign(Long fileId, long expiresAt) {
        if (signingSecret.length == 0) {
            throw new BusinessException("FILE_FORBIDDEN", "下载签名未配置，拒绝访问");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret, "HmacSHA256"));
            byte[] signature = mac.doFinal((fileId + ":" + expiresAt).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(signature);
        } catch (Exception e) {
            throw new IllegalStateException("下载签名计算失败", e);
        }
    }

    /** 魔数嗅探实际类型；无法识别返回 application/octet-stream */
    static String sniffMime(byte[] content) {
        if (content == null || content.length < 12) {
            return "application/octet-stream";
        }
        if ((content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xD8 && (content[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if ((content[0] & 0xFF) == 0x89 && content[1] == 'P' && content[2] == 'N' && content[3] == 'G') {
            return "image/png";
        }
        if (content[0] == 'G' && content[1] == 'I' && content[2] == 'F' && content[3] == '8') {
            return "image/gif";
        }
        if (content[0] == 'B' && content[1] == 'M') {
            return "image/bmp";
        }
        if (content[0] == 'R' && content[1] == 'I' && content[2] == 'F' && content[3] == 'F'
                && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P') {
            return "image/webp";
        }
        if (content[0] == '%' && content[1] == 'P' && content[2] == 'D' && content[3] == 'F') {
            return "application/pdf";
        }
        if (content[0] == 'P' && content[1] == 'K' && (content[2] & 0xFF) == 0x03 && (content[3] & 0xFF) == 0x04) {
            return "application/zip";
        }
        if (content[0] == 'I' && content[1] == 'D' && content[2] == '3') {
            return "audio/mpeg";
        }
        if ((content[4] & 0xFF) == 0x66 && (content[5] & 0xFF) == 0x74 && (content[6] & 0xFF) == 0x79
                && (content[7] & 0xFF) == 0x70) {
            return "video/mp4";
        }
        return "application/octet-stream";
    }

    /** 嗅探类型必须与扩展名声明匹配；SVG 一律拒绝（FILE-01 默认策略） */
    private boolean sniffedMimeAllowed(String sniffedMime, String declaredExtension) {
        if ("svg".equals(declaredExtension)) {
            return false;
        }
        return switch (declaredExtension) {
            case "jpg", "jpeg" -> "image/jpeg".equals(sniffedMime);
            case "png" -> "image/png".equals(sniffedMime);
            case "gif" -> "image/gif".equals(sniffedMime);
            case "bmp" -> "image/bmp".equals(sniffedMime);
            case "webp" -> "image/webp".equals(sniffedMime);
            case "pdf" -> "application/pdf".equals(sniffedMime);
            case "zip", "docx", "xlsx", "pptx" -> "application/zip".equals(sniffedMime);
            case "mp3" -> "audio/mpeg".equals(sniffedMime);
            case "mp4" -> "video/mp4".equals(sniffedMime);
            // 非媒体类（doc/xls/ppt/rar/7z 等无可靠魔数映射）按二进制放行
            default -> "application/octet-stream".equals(sniffedMime)
                    || !"application/octet-stream".equals(sniffedMime);
        };
    }

    private String extensionOf(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot < 0 || dot == filename.length() - 1 ? "" : filename.substring(dot + 1).toLowerCase();
    }

    private String sanitizeOriginalName(String name) {
        if (name == null) return "file";
        String cleaned = name.replaceAll("[\\\\/]", "_").trim();
        return cleaned.isEmpty() ? "file" : cleaned.substring(0, Math.min(cleaned.length(), 255));
    }

    private String sha256HexOf(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 计算失败", e);
        }
    }
}
