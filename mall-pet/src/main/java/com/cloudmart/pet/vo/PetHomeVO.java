package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 我的家园面板（三期）：房间状态 + 已摆放 + 背包家具 + 家园商城 + 舒适度加成说明。
 *
 * <p>{@code dailyEnterRewarded} 表示今天是否已领"回家"奖励（服务端一次/天，客户端只展示）。</p>
 */
@Schema(description = "我的家园面板")
public record PetHomeVO(
        @Schema(description = "宠物 ID") Long petId,
        @Schema(description = "宠物名") String petName,
        @Schema(description = "墙纸编码（null = 默认）") String wallCode,
        @Schema(description = "地板编码（null = 默认）") String floorCode,
        @Schema(description = "欢迎语") String welcomeMessage,
        @Schema(description = "是否允许来访") Boolean isPublic,
        @Schema(description = "当前舒适度") Integer comfort,
        @Schema(description = "累计来访次数") Integer visitCount,
        @Schema(description = "累计点赞数") Integer likeCount,
        @Schema(description = "网格宽（列）") Integer gridWidth,
        @Schema(description = "网格高（行）") Integer gridHeight,
        @Schema(description = "舒适度达到该值后享受休息加成") Integer comfortBonusThreshold,
        @Schema(description = "当前休息心情加成") Integer comfortRestHappinessBonus,
        @Schema(description = "今日回家奖励是否已领") Boolean dailyEnterRewarded,
        @Schema(description = "已摆放家具") List<PetHomeItemVO> placed,
        @Schema(description = "背包中的家具（未摆放也可摆放）") List<PetHomeItemVO> inventory,
        @Schema(description = "家园商城（含未拥有与拥有状态）") List<PetHomeItemVO> shop
) {
}
