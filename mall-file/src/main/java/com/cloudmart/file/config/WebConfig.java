package com.cloudmart.file.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地文件静态资源映射：/{@code /files/**} → 本地存储根目录（file.storage-path）。
 *
 * <p>上传接口返回的相对 URL（/files/pic/20260917/xxx.jpg）经网关 /files/** 路由转发到本服务，
 * 由该映射直接输出服务器本地文件，替代原阿里云 OSS 的域名直链。</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final Path storageRoot;

    public WebConfig(@Value("${file.storage-path:../files}") String storagePath) {
        this.storageRoot = Paths.get(storagePath).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = storageRoot.toUri().toString();
        registry.addResourceHandler("/files/**")
                .addResourceLocations(location);
    }
}