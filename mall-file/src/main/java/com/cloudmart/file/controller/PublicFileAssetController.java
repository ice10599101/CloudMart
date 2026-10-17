package com.cloudmart.file.controller;

import com.cloudmart.file.repository.FileAssetMapper;
import com.cloudmart.file.service.FileService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 本地与 MinIO 存储共用的公开资产读取入口。 */
@RestController
public class PublicFileAssetController {
    private final FileAssetMapper assetMapper;
    private final FileService fileService;

    public PublicFileAssetController(FileAssetMapper assetMapper, FileService fileService) {
        this.assetMapper = assetMapper;
        this.fileService = fileService;
    }

    @GetMapping("/files/by-id/{assetId}")
    public ResponseEntity<byte[]> serveById(@PathVariable Long assetId) {
        var asset = assetMapper.selectById(assetId);
        if (asset == null || !"READY".equals(asset.getStatus())) {
            return ResponseEntity.notFound().build();
        }
        if (!"PUBLIC".equals(asset.getVisibility()) || asset.getStorageKey() == null
                || !asset.getStorageKey().startsWith("public/")) {
            return ResponseEntity.status(403).build();
        }
        byte[] content = fileService.readByStorageKey(asset.getStorageKey());
        if (content == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.getMime()))
                .header("X-Content-Type-Options", "nosniff")
                .body(content);
    }
}
