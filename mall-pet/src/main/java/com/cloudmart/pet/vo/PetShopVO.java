package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 宠物商城视图（商品列表 + 余额 + 货币名）。
 *
 * <p>{@code balance} 为 null 表示余额服务降级（Fail-Open）：前端隐藏余额但保留浏览，
 * 购买时由服务端拒绝或报错。C12 起钱包仅 PET_COIN（宠物币）一种模式，
 * {@code currency} 固定为 PET_COIN（维护窗口余额为 null）。</p>
 */
@Schema(description = "宠物商城")
public record PetShopVO(
        @Schema(description = "余额（null=余额服务降级，前端隐藏）") Long balance,
        @Schema(description = "余额币种：固定 PET_COIN（宠物币）") String currency,
        @Schema(description = "商品列表（装备/皮肤/技能书/食物）") List<PetShopItemVO> items
) {
}
