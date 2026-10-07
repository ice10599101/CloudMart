package com.cloudmart.file.controller;

import com.cloudmart.file.service.impl.MinioFileServiceImpl;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Map;

/**
 * P1-19：MinIO 模式下 /files/** 公共对象流式代理（替代本地静态映射 WebConfig）。
 *
 * <p>URL 形态与本地模式完全一致（前端/历史内容零改动）：对象键 = public/ + 路径。
 * 只读 GET；私有域（private/**）不在本映射下，仍走签名授权下载通道。</p>
 *
 * <p>性能说明：当前经 mall-file 代理一跳；流量上来后可将网关 /files/** 直切
 * MinIO 公网端点（本控制器与 URL 形态均不变，只是前端直达）。</p>
 */
@RestController
@RequestMapping("/files")
@ConditionalOnProperty(name = "file.storage-mode", havingValue = "minio")
public class MinioPublicFileController {

    private static final Logger log = LoggerFactory.getLogger(MinioPublicFileController.class);

    private static final Map<String, MediaType> CONTENT_TYPES = Map.ofEntries(
            Map.entry("jpg", MediaType.IMAGE_JPEG), Map.entry("jpeg", MediaType.IMAGE_JPEG),
            Map.entry("png", MediaType.IMAGE_PNG), Map.entry("gif", MediaType.IMAGE_GIF),
            Map.entry("webp", MediaType.parseMediaType("image/webp")),
            Map.entry("svg", MediaType.parseMediaType("image/svg+xml")),
            Map.entry("pdf", MediaType.APPLICATION_PDF),
            Map.entry("mp4", MediaType.parseMediaType("video/mp4")),
            Map.entry("mp3", MediaType.parseMediaType("audio/mpeg")));

    private final MinioClient minioClient;
    private final String bucket;

    public MinioPublicFileController(
            @Value("${file.minio.endpoint:http://127.0.0.1:9000}") String endpoint,
            @Value("${file.minio.access-key:minioadmin}") String accessKey,
            @Value("${file.minio.secret-key:minioadmin}") String secretKey,
            @Value("${file.minio.bucket:cloudmart-files}") String bucket) {
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        this.bucket = bucket;
    }

    /** 复用服务实现类的启动校验语义：桶不可用直接暴露配置问题 */
    @PostConstruct
    void checkBucket() {
        try {
            boolean exists = minioClient.bucketExists(
                    io.minio.BucketExistsArgs.builder().bucket(bucket).build());
            log.info("P1-19 /files 流式代理就绪, bucket={}, exists={}", bucket, exists);
        } catch (Exception e) {
            throw new IllegalStateException("P1-19 MinIO 连接失败（/files 代理不可用）: " + e.getMessage(), e);
        }
    }

    @GetMapping("/**")
    public ResponseEntity<byte[]> serve(jakarta.servlet.http.HttpServletRequest request) {
        // /files/{path} → 对象键 public/{path}（静态映射语义对齐本地模式）
        String fullPath = request.getRequestURI();
        String relative = fullPath.substring("/files/".length());
        if (relative.isBlank() || relative.contains("..")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String objectKey = MinioFileServiceImpl.DOMAIN_PUBLIC + "/" + relative;

        try {
            minioClient.statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }

        try (var stream = minioClient.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
            byte[] content = stream.readAllBytes();
            String ext = relative.contains(".")
                    ? relative.substring(relative.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
                    : "";
            MediaType mediaType = CONTENT_TYPES.getOrDefault(ext, MediaType.APPLICATION_OCTET_STREAM);
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .cacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofHours(1)))
                    .body(content);
        } catch (Exception e) {
            log.error("P1-19 /files 读取失败: key={}, err={}", objectKey, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
