package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 宠物商城视图（商品列表 + 余额 + 货币名）。
 *
 * <p>{@code balance} 为 null 表示余额服务降级（Fail-Open）：前端隐藏余额但保留浏览，
 * 购买时由服务端拒绝或报错。{@code currency} 标明余额币种（PET_COIN=宠物币 /
 * STARLIGHT=社区星光），随钱包模式自动切换——字段名不再绑定具体币种。</p>
 */
@Schema(description = "宠物商城")
public record PetShopVO(
        @Schema(description = "余额（null=余额服务降级，前端隐藏）") Integer balance,
        @Schema(description = "余额币种：PET_COIN=宠物币 / STARLIGHT=社区星光") String currency,
        @Schema(description = "商品列表（装备/皮肤/技能书/食物）") List<PetShopItemVO> items
) {
}
