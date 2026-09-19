package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * 宠物数据看板（三期，管理端）。
 *
 * <p>口径说明（避免"看板数字对不上"）：
 * <ul>
 *   <li>活跃宠物 = 当日/近 7 日有任意行为流水（pet_activity）的去重宠物数；</li>
 *   <li>消费侧只统计"宠物物品购买次数"（pet_inventory 按类型），星光收支以 mall-wish 流水为准；</li>
 *   <li>趋势按 UTC 自然日聚合，与业务写入时区一致。</li>
 * </ul>
 */
@Schema(description = "宠物数据看板")
public record PetDashboardVO(
        @Schema(description = "概览指标") Overview overview,
        @Schema(description = "近 N 日趋势") List<TrendPoint> trend,
        @Schema(description = "分布与排行") Distribution distribution
) {
    /** 概览指标 */
    @Schema(description = "宠物看板概览")
    public record Overview(
            long totalPets,
            long newPetsToday,
            long newPets7d,
            long activePetsToday,
            long activePets7d,
            long totalBattles,
            long battlesToday,
            long totalBottles,
            long bottlesToday,
            long totalVisits,
            long totalFriendVisits,
            long totalWallMessages,
            long wallMessagesToday,
            long totalRelations,
            long totalFriends,
            long totalRooms,
            long avgComfort,
            long questsClaimedToday,
            long questsGeneratedToday,
            long careerHired,
            @Schema(description = "宠物物品购买次数（按类型：EQUIPMENT/SKIN/SKILL_BOOK/FURNITURE）")
            Map<String, Long> purchasesByType
    ) {
    }

    /** 趋势点（UTC 自然日） */
    @Schema(description = "宠物看板趋势点")
    public record TrendPoint(
            String date,
            long newPets,
            long activePets,
            long activities,
            long wallMessages,
            long visits,
            long battles
    ) {
    }

    /** 分布与排行 */
    @Schema(description = "宠物看板分布")
    public record Distribution(
            @Schema(description = "种类分布") List<Bucket> species,
            @Schema(description = "等级分布（幼年/成长/成年）") List<Bucket> levelBuckets,
            @Schema(description = "职业分布 Top") List<Bucket> careers,
            @Schema(description = "家具摆放 Top") List<Bucket> topFurniture,
            @Schema(description = "亲密度 Top（宠物 ID → 点数）") List<Bucket> topIntimacy
    ) {
    }

    /** 通用分布项 */
    @Schema(description = "分布项")
    public record Bucket(String name, long value) {
    }
}
