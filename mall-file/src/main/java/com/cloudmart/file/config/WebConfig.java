package com.cloudmart.file.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地文件静态资源映射（S01 存储域隔离）：{@code /files/**} → {@code {file.storage-path}/public}。
 *
 * <p>PRIVATE/QUARANTINE 存储域不在任何静态映射下——私有附件仅凭 HMAC 短期签名
 * 经 /assets/{id}/download 授权通道读取（下载路径由 /assets/{id}/download-url 取得）。</p>
 */
@Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "file.storage-mode", havingValue = "local", matchIfMissing = true)
public class WebConfig implements WebMvcConfigurer {

    private final Path storageRoot;

    public WebConfig(@Value("${file.storage-path:../files}") String storagePath) {
        // S01：静态资源仅映射 public 存储域（PRIVATE/QUARANTINE 不对外）
        this.storageRoot = Paths.get(storagePath, "public").toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = storageRoot.toUri().toString();
        registry.addResourceHandler("/files/**")
                .addResourceLocations(location)
                // S01：显式只读且禁止目录列表（Spring 资源处理器默认不列目录，防卸载语义写明）
                .setCachePeriod(3600);
    }
}