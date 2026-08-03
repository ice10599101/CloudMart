package com.cloudmart.file.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.file.service.FileService;
import lombok.extern.slf4j.Slf4j;
import org.dromara.x.file.storage.core.FileStorageService;
import org.dromara.x.file.storage.core.upload.UploadPretreatment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;

@Slf4j
@Service
public class OssFileServiceImpl implements FileService {

    private final FileStorageService fileStorageService;
    private final Set<String> allowedExtensions;
    private final long maxSize;

    public OssFileServiceImpl(
            FileStorageService fileStorageService,
            @Value("${file.allowed-extensions:jpg,jpeg,png,gif,bmp,webp,svg,pdf,doc,docx,xls,xlsx,ppt,pptx,zip,rar,7z,mp4,mp3}") String allowedExtensions,
            @Value("${file.max-size:52428800}") long maxSize) {
        this.fileStorageService = fileStorageService;
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

        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        UploadPretreatment pretreatment = fileStorageService.of(file)
                .setPath(datePath + "/");

        try {
            var fileInfo = pretreatment.upload();
            if (fileInfo == null) {
                throw new BusinessException("FILE_UPLOAD_FAILED", "文件上传失败");
            }
            return fileInfo.getUrl();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("文件上传异常: {}", e.getMessage(), e);
            throw new BusinessException("FILE_UPLOAD_FAILED", "文件上传失败: " + e.getMessage());
        }
    }

    @Override
    public void delete(String url) {
        if (url == null || url.isBlank()) {
            throw new BusinessException("FILE_URL_EMPTY", "文件 URL 不能为空");
        }
        fileStorageService.delete(url);
    }

    private String extractExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1);
    }
}
