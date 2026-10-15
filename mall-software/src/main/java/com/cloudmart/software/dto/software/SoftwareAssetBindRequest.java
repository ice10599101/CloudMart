package com.cloudmart.software.dto.software;

import java.util.List;

/**
 * 文件素材引用绑定请求（方案 8.2/9.6，mall-file 内部契约镜像）：
 * 约束由本服务声明、文件服务按资产台账裁决；bizType 仅限
 * SOFTWARE_REVISION / SOFTWARE_REPORT。
 */
public record SoftwareAssetBindRequest(
        String bizType,
        String bizId,
        Long ownerUserId,
        List<String> allowedMimes,
        Long maxSizeBytes,
        String requireVisibility) {

    /** 软件域允许的引用类型（文件服务侧同样校验，双保险） */
    public static final List<String> ALLOWED_BIZ_TYPES = List.of("SOFTWARE_REVISION", "SOFTWARE_REPORT");

    /** 图标约束：PNG/JPEG/WebP ≤2MB */
    public static SoftwareAssetBindRequest icon(Long revisionId, Long ownerUserId) {
        return new SoftwareAssetBindRequest("SOFTWARE_REVISION", String.valueOf(revisionId),
                ownerUserId, List.of("image/png", "image/jpeg", "image/webp"), 2L * 1024 * 1024, null);
    }

    /** 截图约束：≤5MB */
    public static SoftwareAssetBindRequest screenshot(Long revisionId, Long ownerUserId) {
        return new SoftwareAssetBindRequest("SOFTWARE_REVISION", String.valueOf(revisionId),
                ownerUserId, List.of("image/png", "image/jpeg", "image/webp"), 5L * 1024 * 1024, null);
    }

    /** 正文媒体：与截图同限 */
    public static SoftwareAssetBindRequest bodyMedia(Long revisionId, Long ownerUserId) {
        return new SoftwareAssetBindRequest("SOFTWARE_REVISION", String.valueOf(revisionId),
                ownerUserId, List.of("image/png", "image/jpeg", "image/webp", "image/gif"),
                5L * 1024 * 1024, null);
    }

    /** 举报证据：仅本人素材 */
    public static SoftwareAssetBindRequest reportEvidence(Long reportId, Long reporterId) {
        return new SoftwareAssetBindRequest("SOFTWARE_REPORT", String.valueOf(reportId),
                reporterId, List.of("image/png", "image/jpeg", "image/webp"), 5L * 1024 * 1024, null);
    }
}
