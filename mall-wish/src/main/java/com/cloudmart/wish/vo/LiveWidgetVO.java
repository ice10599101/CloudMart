package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 直播心愿挂件数据（Sprint 3.4，公开接口；Redis 缓存 TTL 10s 支撑
 * 1000 观众轮询；不暴露主播手机号/邮箱等隐私）。
 *
 * <p>降级：全局开关（灰度 feature=wish_live_widget 比例 0）或主播配置
 * is_visible=false → visible=false，前端隐藏挂件；主播无进行中心愿 →
 * hasWish=false，前端展示"去许愿"引导。</p>
 */
@Schema(description = "直播心愿挂件数据")
public record LiveWidgetVO(
        Long streamerId,
        Boolean visible,
        Boolean hasWish,
        Long wishId,
        String title,
        Integer progressCurrent,
        Integer progressTarget,
        Integer progressPercentage,
        Integer checkinDays,
        Integer starlightBalance,
        String position,
        String styleConfig
) {
}
