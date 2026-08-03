package com.cloudmart.file.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.file.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Tag(name = "文件管理", description = "文件上传、删除与预览")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/upload")
    @Operation(summary = "上传文件", description = "上传文件到阿里云 OSS，返回访问 URL")
    public ApiResponse<FileUploadResponse> upload(
            @Parameter(description = "上传文件", required = true)
            @RequestParam("file") MultipartFile file) {
        String url = fileService.upload(file);
        return ApiResponse.ok(new FileUploadResponse(url, file.getOriginalFilename(), file.getSize()));
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
