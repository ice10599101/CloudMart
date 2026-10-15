package com.cloudmart.software.vo.software;

import java.time.LocalDate;
import java.util.List;

/**
 * 版本与来源摘要（方案 3.3/7.2）：默认稳定版在前（release_order 服务端排序）；
 * 测试版单独标识。
 */
public record SoftwareReleaseVO(
        Long releaseId,
        String releaseKey,
        String versionName,
        String channel,
        LocalDate releaseDate,
        String changelog,
        Boolean recommended,
        List<SoftwareSourceSummaryVO> sources) {
}
