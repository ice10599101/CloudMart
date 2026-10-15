package com.cloudmart.software.vo.software;

/**
 * 来源摘要（方案 7.2）：只有类型/平台/可用性等展示事实；
 * 无 URL、无提取码、无解压密码——真实信息仅 resolve 授权响应返回。
 */
public record SoftwareSourceSummaryVO(
        Long sourceId,
        String sourceKey,
        String sourceType,
        String provider,
        String platform,
        String arch,
        String label,
        String sizeLabel,
        /** 发布者提供的 SHA-256（页面标注"发布者提供"，未经本站验证） */
        String claimedSha256,
        /** 来源说明（不含秘密） */
        String note,
        /** 展示可用性（UNKNOWN/AVAILABLE/SUSPECTED/INVALID/DISABLED，按指纹匹配应用） */
        String availability) {
}
