package com.cloudmart.software.vo.software;

/**
 * 评分结果与聚合（P1 4.5）：零票 ratingAvg 为 null，前端显示"暂无评分"；
 * 均分保留一位小数（服务端计算）。
 */
public record SoftwareRatingVO(Long softwareId, Integer myScore, Integer ratingCount,
                               Double ratingAvg) {
}
