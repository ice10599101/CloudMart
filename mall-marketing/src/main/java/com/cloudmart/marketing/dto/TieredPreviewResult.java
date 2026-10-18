package com.cloudmart.marketing.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * T10：满减活动试算结果（纯只读，不预占券/库存、不写订单、不消耗活动额度）。
 *
 * @param matched          是否命中阶梯规则（忽略活动状态/时间窗，便于草稿预览）
 * @param userVisible      用户侧当前是否可用（活动启用且处于时间窗内）
 * @param promotionId      活动 ID
 * @param promotionName    活动名称
 * @param promotionStatus  活动状态（ENABLED/DISABLED）
 * @param matchedRuleId    命中规则 ID（未命中为空）
 * @param minAmount        命中门槛（未命中为空）
 * @param discountAmount   命中优惠金额（未命中为 0）
 * @param orderAmount      本次试算使用的购物篮金额
 * @param reasons          命中/未命中解释
 * @param blockingReasons  用户侧不可用原因（未启用/未开始/已过期）
 * @param configUpdatedAt  活动配置最后更新时间（预览依据的配置版本）
 * @param previewAt        试算时点
 */
public record TieredPreviewResult(
        boolean matched,
        boolean userVisible,
        Long promotionId,
        String promotionName,
        String promotionStatus,
        Long matchedRuleId,
        BigDecimal minAmount,
        BigDecimal discountAmount,
        BigDecimal orderAmount,
        List<String> reasons,
        List<String> blockingReasons,
        LocalDateTime configUpdatedAt,
        LocalDateTime previewAt) {
}
