package com.cloudmart.file.recorder;

import lombok.extern.slf4j.Slf4j;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.recorder.FileRecorder;
import org.dromara.x.file.storage.core.upload.FilePartInfo;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class RedisFileRecorder implements FileRecorder {

    private static final String KEY_PREFIX = "file:info:";
    private static final String PART_KEY_PREFIX = "file:part:";
    private static final long FILE_TTL_DAYS = 30;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisFileRecorder(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean save(FileInfo fileInfo) {
        try {
            String json = objectMapper.writeValueAsString(fileInfo);
            String key = KEY_PREFIX + fileInfo.getUrl();
            redisTemplate.opsForValue().set(key, json, FILE_TTL_DAYS, TimeUnit.DAYS);
            log.debug("Saved file info: url={}", fileInfo.getUrl());
            return true;
        } catch (Exception e) {
            log.error("Failed to save file info: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public void update(FileInfo fileInfo) {
        save(fileInfo);
    }

    @Override
    public FileInfo getByUrl(String url) {
        try {
            String json = redisTemplate.opsForValue().get(KEY_PREFIX + url);
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, FileInfo.class);
        } catch (Exception e) {
            log.error("Failed to get file info by url: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public boolean delete(String url) {
        try {
            Boolean deleted = redisTemplate.delete(KEY_PREFIX + url);
            log.debug("Deleted file info: url={}, deleted={}", url, deleted);
            return Boolean.TRUE.equals(deleted);
        } catch (Exception e) {
            log.error("Failed to delete file info: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public void saveFilePart(FilePartInfo filePartInfo) {
        try {
            String json = objectMapper.writeValueAsString(filePartInfo);
            String key = PART_KEY_PREFIX + filePartInfo.getUploadId() + ":" + filePartInfo.getPartNumber();
            redisTemplate.opsForValue().set(key, json, 1, TimeUnit.DAYS);
        } catch (Exception e) {
            log.error("Failed to save file part info: {}", e.getMessage(), e);
        }
    }

    @Override
    public void deleteFilePartByUploadId(String uploadId) {
        try {
            var keys = redisTemplate.keys(PART_KEY_PREFIX + uploadId + ":*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.error("Failed to delete file parts by uploadId: {}", e.getMessage(), e);
        }
    }
}
