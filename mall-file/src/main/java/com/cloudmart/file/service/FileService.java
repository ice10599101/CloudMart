package com.cloudmart.file.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileService {

    /**
     * S01 唯一资产存储通道：按可见性分域落盘（public/private），校验先行，
     * 内容只读入内存一次。
     *
     * @return 存储键（含域前缀）
     */
    String store(byte[] content, String originalFilename, String visibility);

    /** PUBLIC 存储键 → 对外静态 URL；私有/未知键返回 null（私有资源无公开 URL） */
    String publicUrlOf(String storageKey);

    /** 按存储键删除（路径穿越防护） */
    void deleteByStorageKey(String storageKey);

    /** FILE-01：按存储键读取文件内容（下载授权通道用）；不存在返回 null */
    byte[] readByStorageKey(String storageKey);
}
