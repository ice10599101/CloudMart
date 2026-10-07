package com.cloudmart.file.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.file.service.FileService;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * MinIO 对象存储实现（P1-19，file.storage-mode=minio 时启用）。
 *
 * <p>存储键布局与本地实现完全一致（{@code public|private/{category}/{yyyyMMdd}/{uuid.ext}}），
 * 迁移脚本按键镜像搬迁，双模式可随时互切；存量数据迁移期间本地实现仍可读取旧文件。</p>
 *
 * <p>URL 兼容策略：{@link #publicUrlOf} 恒返回 {@code /files/**} 相对路径（与本地模式同形），
 * 公共对象由 {@code MinioPublicFileController} 流式代理——前端 {@code resolveFileUrl}
 * 与历史帖子内嵌 URL 全部零改动；性能敏感场景后续可将网关 /files/** 直切 MinIO 公网端点。</p>
 *
 * <p>失败策略：bucket 启动时确保存在（缺则建）；对象读写失败按原异常语义抛
 * FILE_UPLOAD_FAILED/FILE_DELETE_FAILED（fail-fast，不静默降级到本地盘）。</p>
 */
@Service
@ConditionalOnProperty(name = "file.storage-mode", havingValue = "minio")
public class MinioFileServiceImpl implements FileService {

    private static final Logger log = LoggerFactory.getLogger(MinioFileServiceImpl.class);

    public static final String DOMAIN_PUBLIC = "public";
    public static final String DOMAIN_PRIVATE = "private";
    /** 静态资源 URL 前缀（与本地模式一致，兼容前端 resolveFileUrl 与历史内容） */
    public static final String PUBLIC_URL_PREFIX = "/files";

    private static final Map<String, String> EXTENSION_CATEGORY = Map.ofEntries(
            Map.entry("jpg", "pic"), Map.entry("jpeg", "pic"), Map.entry("png", "pic"),
            Map.entry("gif", "pic"), Map.entry("bmp", "pic"), Map.entry("webp", "pic"),
            Map.entry("svg", "pic"), Map.entry("mp3", "music"), Map.entry("mp4", "video"));

    private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
            Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"), Map.entry("png", "image/png"),
            Map.entry("gif", "image/gif"), Map.entry("bmp", "image/bmp"), Map.entry("webp", "image/webp"),
            Map.entry("svg", "image/svg+xml"), Map.entry("pdf", "application/pdf"),
            Map.entry("mp4", "video/mp4"), Map.entry("mp3", "audio/mpeg"),
            Map.entry("webm", "video/webm"), Map.entry("ogg", "audio/ogg"));

    private final MinioClient minioClient;
    private final String bucket;
    private final Set<String> allowedExtensions;
    private final long maxSize;

    public MinioFileServiceImpl(
            @Value("${file.minio.endpoint:http://127.0.0.1:9000}") String endpoint,
            @Value("${file.minio.access-key:minioadmin}") String accessKey,
            @Value("${file.minio.secret-key:minioadmin}") String secretKey,
            @Value("${file.minio.bucket:cloudmart-files}") String bucket,
            @Value("${file.allowed-extensions:jpg,jpeg,png,gif,bmp,webp,pdf,doc,docx,xls,xlsx,ppt,pptx,zip,rar,7z,mp4,mp3}") String allowedExtensions,
            @Value("${file.max-size:52428800}") long maxSize) {
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        this.bucket = bucket;
        this.allowedExtensions = Set.of(allowedExtensions.split(","));
        this.maxSize = maxSize;
    }

    /** 启动时确保 bucket 存在（缺则建；MinIO 不可用 fail-fast 暴露配置问题） */
    @PostConstruct
    void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("P1-19 MinIO bucket 已创建: {}", bucket);
            }
        } catch (Exception e) {
            throw new IllegalStateException("P1-19 MinIO 连接/建桶失败（endpoint/bucket 配置核对）: " + e.getMessage(), e);
        }
    }

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

    @Override
    public String store(byte[] content, String originalFilename, String visibility) {
        validateDeclaration(content, originalFilename);
        String extension = extractExtension(originalFilename);
        String domain = "PRIVATE".equalsIgnoreCase(visibility) ? DOMAIN_PRIVATE : DOMAIN_PUBLIC;

        String category = EXTENSION_CATEGORY.getOrDefault(extension, "file");
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        String storageKey = domain + "/" + category + "/" + datePath + "/" + storedName;

        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(storageKey)
                    .contentType(CONTENT_TYPES.getOrDefault(extension, "application/octet-stream"))
                    .stream(new ByteArrayInputStream(content), (long) content.length, -1L)
                    .build());
        } catch (Exception e) {
            log.error("P1-19 MinIO 上传失败: {}", e.getMessage(), e);
            throw new BusinessException("FILE_UPLOAD_FAILED", "文件上传失败: " + e.getMessage());
        }
        log.info("P1-19 MinIO 对象已保存: {} ({} bytes)", storageKey, content.length);
        return storageKey;
    }

    /** PUBLIC 存储键 → /files/{key 去掉 public/ 前缀}（与本地模式同形，零客户端改动） */
    @Override
    public String publicUrlOf(String storageKey) {
        if (storageKey == null || !storageKey.startsWith(DOMAIN_PUBLIC + "/")) {
            return null;
        }
        return PUBLIC_URL_PREFIX + "/" + storageKey.substring(DOMAIN_PUBLIC.length() + 1);
    }

    @Override
    public void deleteByStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new BusinessException("FILE_URL_EMPTY", "存储键不能为空");
        }
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(storageKey).build());
            log.info("P1-19 MinIO 对象已删除: {}", storageKey);
        } catch (Exception e) {
            log.error("P1-19 MinIO 删除失败: {}", e.getMessage(), e);
            throw new BusinessException("FILE_DELETE_FAILED", "文件删除失败: " + e.getMessage());
        }
    }

    @Override
    public byte[] readByStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return null;
        }
        try (var stream = minioClient.getObject(GetObjectArgs.builder().bucket(bucket).object(storageKey).build())) {
            return stream.readAllBytes();
        } catch (Exception e) {
            log.error("P1-19 MinIO 读取失败: key={}, err={}", storageKey, e.getMessage());
            return null;
        }
    }

    private String extractExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot < 0 || dot == filename.length() - 1 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
