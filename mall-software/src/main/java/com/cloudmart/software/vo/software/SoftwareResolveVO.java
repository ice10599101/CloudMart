package com.cloudmart.software.vo.software;

/**
 * 获取链接结果（方案 5.2 ResolveVO）：唯一可返回真实链接/提取码/解压密码的
 * 用户端响应；resolve 设置 Cache-Control: no-store。
 */
public record SoftwareResolveVO(
        Long sourceId,
        String sourceKey,
        String sourceType,
        String provider,
        String platform,
        String arch,
        String label,
        String url,
        String extractionCode,
        String archivePassword,
        String sizeLabel,
        /** 展示可用性（UNKNOWN/SUSPECTED/AVAILABLE） */
        String availability,
        /** 非AVAILABLE 时的提示文案（UNKNOWN/SUSPECTED；不代替安全认证） */
        String availabilityTip) {
}
