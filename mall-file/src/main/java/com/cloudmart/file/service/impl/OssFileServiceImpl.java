package com.cloudmart.file.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.file.service.FileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 本地文件存储（S01 存储域隔离）：
 *
 * <ul>
 *   <li>存储根按可见性分域——{@code {root}/public/**}（静态资源 /files/** 唯一映射根）
 *       与 {@code {root}/private/**}（不在任何静态映射下，仅签名授权下载通道可读）；
 *       校验失败/落盘失败的临时对象落 {@code {root}/quarantine/**} 并立即清理；</li>
 *   <li>校验先行：扩展名/大小/内容校验全部通过才落盘（原"先落盘再校验，失败未删实物"
 *       的孤儿文件路径消除）；内容只读入内存一次（嗅探/哈希/落盘复用，避免多次 getBytes）；</li>
 *   <li>删除只按 storageKey 执行并做路径穿越归一化校验；按 URL 删除的旧通道已删除（LC05）。</li>
 * </ul>
 */
@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "file.storage-mode", havingValue = "local", matchIfMissing = true)
public class OssFileServiceImpl implements FileService {

    private static final Logger log = LoggerFactory.getLogger(OssFileServiceImpl.class);

    public static final String DOMAIN_PUBLIC = "public";
    public static final String DOMAIN_PRIVATE = "private";
    public static final String DOMAIN_QUARANTINE = "quarantine";
    /** 静态资源 URL 前缀（WebConfig 仅映射 {root}/public） */
    public static final String PUBLIC_URL_PREFIX = "/files";

    private static final Map<String, String> EXTENSION_CATEGORY = Map.ofEntries(
            Map.entry("jpg", "pic"), Map.entry("jpeg", "pic"), Map.entry("png", "pic"),
            Map.entry("gif", "pic"), Map.entry("bmp", "pic"), Map.entry("webp", "pic"),
            Map.entry("svg", "pic"), Map.entry("mp3", "music"), Map.entry("mp4", "video"));

        private final Path storageRoot;
    private final Set<String> allowedExtensions;
    private final long maxSize;

    public OssFileServiceImpl(
            @Value("${file.storage-path:../files}") String storagePath,
            @Value("${file.allowed-extensions:jpg,jpeg,png,gif,bmp,webp,pdf,doc,docx,xls,xlsx,ppt,pptx,zip,rar,7z,mp4,mp3}") String allowedExtensions,
            @Value("${file.max-size:52428800}") long maxSize) {
        this.storageRoot = Paths.get(storagePath).toAbsolutePath().normalize();
        this.allowedExtensions = Set.of(allowedExtensions.split(","));
        this.maxSize = maxSize;
    }

    /** 声明校验（扩展名/大小/非空）；内容魔数校验由 FileAssetService.validateContent 先行执行 */
    private void validateDeclaration(byte[] content, String originalFilename) {
        if (content == null || content.length == 0) {
            throw new BusinessException("FILE_EMPTY", "上传文件不能为空");
        }
        if (content.length > maxSize) {
            throw new BusinessException("FILE_TOO_LARGE", "文件大小超过限制");
        }
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BusinessException("FILE_NAME_INVALID", "文件名不能为空");
        }
        String extension = extractExtension(originalFilename);
        if (!allowedExtensions.contains(extension)) {
            throw new BusinessException("FILE_TYPE_NOT_ALLOWED", "不支持的文件类型: " + extension);
        }
    }

    /**
     * S01 唯一资产存储通道：内容校验通过后按可见性分域落盘。
     *
     * @param content 已读取一次的字节（嗅探/哈希/落盘复用）
     * @return 存储键（含 public/ 前缀；PUBLIC 对外 URL 由调用方剥离前缀拼 /files/）
     */
    @Override
    public String store(byte[] content, String originalFilename, String visibility) {
        validateDeclaration(content, originalFilename);
        String extension = extractExtension(originalFilename);
        String domain = "PRIVATE".equalsIgnoreCase(visibility) ? DOMAIN_PRIVATE : DOMAIN_PUBLIC;

        String category = EXTENSION_CATEGORY.getOrDefault(extension, "file");
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        String storageKey = domain + "/" + category + "/" + datePath + "/" + storedName;

        Path target = storageRoot.resolve(storageKey).normalize();
        if (!target.startsWith(storageRoot)) {
            throw new BusinessException("FILE_NAME_INVALID", "存储路径非法");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            // 落盘失败：尝试清理半成品（隔离域语义），避免孤儿实物
            quarantineCleanup(target);
            log.error("本地文件保存失败: {}", e.getMessage(), e);
            throw new BusinessException("FILE_UPLOAD_FAILED", "文件上传失败: " + e.getMessage());
        }
        log.info("本地文件已保存: {} ({} bytes)", target, content.length);
        return storageKey;
    }

    /** PUBLIC 存储键 → 对外静态 URL（/files/{key 去掉 public/ 前缀}）；私有键返回 null（无公开 URL） */
    @Override
    public String publicUrlOf(String storageKey) {
        if (storageKey == null || !storageKey.startsWith(DOMAIN_PUBLIC + "/")) {
            return null;
        }
        return PUBLIC_URL_PREFIX + "/" + storageKey.substring(DOMAIN_PUBLIC.length() + 1);
    }

    /** 按存储键删除（路径归一化后必须仍在存储根内） */
    @Override
    public void deleteByStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new BusinessException("FILE_URL_EMPTY", "存储键不能为空");
        }
        Path resolved = storageRoot.resolve(storageKey).normalize();
        if (!resolved.startsWith(storageRoot)) {
            throw new BusinessException("FILE_NAME_INVALID", "存储路径非法");
        }
        try {
            boolean deleted = Files.deleteIfExists(resolved);
            log.info("本地文件删除: key={}, deleted={}", storageKey, deleted);
        } catch (IOException e) {
            log.error("本地文件删除失败: {}", e.getMessage(), e);
            throw new BusinessException("FILE_DELETE_FAILED", "文件删除失败: " + e.getMessage());
        }
    }

    @Override
    public byte[] readByStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return null;
        }
        Path resolved = storageRoot.resolve(storageKey).normalize();
        if (!resolved.startsWith(storageRoot)) {
            return null;
        }
        try {
            return Files.exists(resolved) ? Files.readAllBytes(resolved) : null;
        } catch (IOException e) {
            log.error("本地文件读取失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /** 落盘失败清理半成品 */
    private void quarantineCleanup(Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException cleanupError) {
            log.error("隔离清理失败: {}", target, cleanupError);
        }
    }

    private String extractExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot < 0 || dot == filename.length() - 1 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
