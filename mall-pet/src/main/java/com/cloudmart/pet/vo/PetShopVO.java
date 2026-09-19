package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 宠物商城视图（商品列表 + 星光余额 + 货币名）。
 *
 * <p>{@code starlightBalance} 为 null 表示星光服务降级（Fail-Open）：
 * 前端隐藏余额但保留浏览，购买时由服务端拒绝或报错。</p>
 */
@Schema(description = "宠物商城")
public record PetShopVO(
        @Schema(description = "星光余额（null=余额服务降级，前端隐藏）") Integer starlightBalance,
        @Schema(description = "商品列表（装备/皮肤/技能书）") List<PetShopItemVO> items
) {
}
