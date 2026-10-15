package com.cloudmart.software.vo.software;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 软件卡片（方案 3.2）：不含任何链接/提取码；卡片不直接暴露提取码，
 * 不把"获取链接次数"标成"下载量"。ID 由全局序列化输出为字符串。
 */
public record SoftwareCardVO(
        Long id,
        String name,
        String summary,
        Long iconAssetId,
        Long categoryId,
        String categoryName,
        String licenseType,
        /** 发布来源覆盖的平台去重集 */
        List<String> platforms,
        LocalDateTime lastReleaseAt,
        Integer favoriteCount,
        Integer commentCount,
        Integer ratingCount,
        Double ratingAvg) {
}
