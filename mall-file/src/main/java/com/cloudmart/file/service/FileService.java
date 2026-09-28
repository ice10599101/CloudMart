package com.cloudmart.file.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileService {
    String upload(MultipartFile file);
    void delete(String url);

    /** FILE-01：按存储键读取文件内容（下载授权通道用）；不存在返回 null */
    byte[] readByStorageKey(String storageKey);
}
