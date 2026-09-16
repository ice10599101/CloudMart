package com.cloudmart.file.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.file.service.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 文件服务实现（服务器本地存储）。
 *
 * <p>2026-09-17 起由阿里云 OSS（dromara x-file-storage + aliyun-oss 平台）切换为服务器本地磁盘存储：
 * 文件统一保存到项目根目录 files 下按类型分类的子目录（pic/music/video/file），
 * 对外返回相对 URL {@code /files/{分类}/{yyyyMMdd}/{uuid}.{ext}}，由网关路由到本服务的静态资源映射对外可访问。
 * 原阿里云 OSS 上传/删除代码在下方以注释保留，未删除。</p>
 */
@Slf4j
@Service
public class OssFileServiceImpl implements FileService {

    /** 本地存储根目录的绝对路径（file.storage-path，默认 ../files 即项目根目录 files） */
    private final Path storageRoot;
    private final Set<String> allowedExtensions;
    private final long maxSize;

    /** 扩展名 → files 子目录分类（与根目录 files 下子文件夹名一一对应） */
    private static final Map<String, String> EXTENSION_CATEGORY = Map.ofEntries(
            Map.entry("jpg", "pic"),
            Map.entry("jpeg", "pic"),
            Map.entry("png", "pic"),
            Map.entry("gif", "pic"),
            Map.entry("bmp", "pic"),
            Map.entry("webp", "pic"),
            Map.entry("svg", "pic"),
            Map.entry("mp3", "music"),
            Map.entry("mp4", "video"));

    /** 对外 URL 前缀（网关 /files/** 转发到本服务静态资源映射） */
    private static final String URL_PREFIX = "/files";

    public OssFileServiceImpl(
            @Value("${file.storage-path:../files}") String storagePath,
            @Value("${file.allowed-extensions:jpg,jpeg,png,gif,bmp,webp,svg,pdf,doc,docx,xls,xlsx,ppt,pptx,zip,rar,7z,mp4,mp3}") String allowedExtensions,
            @Value("${file.max-size:52428800}") long maxSize) {
        this.storageRoot = Paths.get(storagePath).toAbsolutePath().normalize();
        this.allowedExtensions = Set.of(allowedExtensions.split(","));
        this.maxSize = maxSize;
    }

    @Override
    public String upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("FILE_EMPTY", "上传文件不能为空");
        }
        if (file.getSize() > maxSize) {
            throw new BusinessException("FILE_TOO_LARGE", "文件大小超过限制");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BusinessException("FILE_NAME_INVALID", "文件名不能为空");
        }
        String extension = extractExtension(originalFilename).toLowerCase();
        if (!allowedExtensions.contains(extension)) {
            throw new BusinessException("FILE_TYPE_NOT_ALLOWED", "不支持的文件类型: " + extension);
        }

        // ================= 原阿里云 OSS 上传实现（已停用，注释保留） =================
        // String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        // UploadPretreatment pretreatment = fileStorageService.of(file)
        //         .setPath(datePath + "/");
        // try {
        //     var fileInfo = pretreatment.upload();
        //     if (fileInfo == null) {
        //         throw new BusinessException("FILE_UPLOAD_FAILED", "文件上传失败");
        //     }
        //     return fileInfo.getUrl();
        // } catch (BusinessException e) {
        //     throw e;
        // } catch (Exception e) {
        //     log.error("文件上传异常: {}", e.getMessage(), e);
        //     throw new BusinessException("FILE_UPLOAD_FAILED", "文件上传失败: " + e.getMessage());
        // }
        // ========================================================================

        String category = EXTENSION_CATEGORY.getOrDefault(extension, "file");
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path targetDir = storageRoot.resolve(category).resolve(datePath);
        Path target = targetDir.resolve(storedName);
        try {
            Files.createDirectories(targetDir);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("本地文件保存失败: {}", e.getMessage(), e);
            throw new BusinessException("FILE_UPLOAD_FAILED", "文件上传失败: " + e.getMessage());
        }
        String url = URL_PREFIX + "/" + category + "/" + datePath + "/" + storedName;
        log.info("本地文件已保存: {} ({} bytes)", target, file.getSize());
        return url;
    }

    @Override
    public void delete(String url) {
        if (url == null || url.isBlank()) {
            throw new BusinessException("FILE_URL_EMPTY", "文件 URL 不能为空");
        }

        // ================= 原阿里云 OSS 删除实现（已停用，注释保留） =================
        // fileStorageService.delete(url);
        // ========================================================================

        String relativePath = toLocalRelativePath(url);
        if (relativePath == null) {
            // 存量 OSS 完整域名 URL：本地无对应文件，跳过删除（幂等成功）
            log.info("跳过非本地文件 URL 的删除: {}", url);
            return;
        }
        try {
            boolean deleted = Files.deleteIfExists(storageRoot.resolve(relativePath));
            log.info("本地文件删除: relativePath={}, deleted={}", relativePath, deleted);
        } catch (IOException e) {
            log.error("本地文件删除失败: {}", e.getMessage(), e);
            throw new BusinessException("FILE_DELETE_FAILED", "文件删除失败: " + e.getMessage());
        }
    }

    /** 将对外 URL 解析为相对存储根的本地路径；非本地 URL 或路径穿越返回 null */
    private String toLocalRelativePath(String url) {
        String prefix = URL_PREFIX + "/";
        int index = url.indexOf(prefix);
        if (index < 0) {
            return null;
        }
        String relative = url.substring(index + prefix.length());
        Path resolved = storageRoot.resolve(relative).normalize();
        if (!resolved.startsWith(storageRoot)) {
            log.warn("拒绝路径穿越删除请求: {}", url);
            return null;
        }
        return storageRoot.relativize(resolved).toString();
    }

    private String extractExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1);
    }
}