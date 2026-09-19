package com.cloudmart.pet.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.pet.dto.BuyItemRequest;
import com.cloudmart.pet.dto.EquipItemRequest;
import com.cloudmart.pet.dto.WearSkinRequest;
import com.cloudmart.pet.service.PetInventoryService;
import com.cloudmart.pet.service.PetShopService;
import com.cloudmart.pet.vo.PetInventoryItemVO;
import com.cloudmart.pet.vo.PetShopVO;
import com.cloudmart.pet.vo.PetVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 宠物商城与背包接口（原文档 §6.1 背包/装备、§89 宠物商城/皮肤）。
 *
 * <p>货币为星光（mall-wish 钱包），购买先入包再扣星光——扣减失败整体回滚，
 * 不出现"扣了星光没拿到物品"（AGENTS §17）。</p>
 */
@RestController
@RequestMapping
@Tag(name = "宠物商城与背包", description = "商城列表/购买、背包、装备穿戴、皮肤穿戴")
@RequiredArgsConstructor
public class PetShopController {

    private final PetShopService shopService;
    private final PetInventoryService inventoryService;

    @GetMapping("/shop")
    @Operation(summary = "宠物商城", description = "装备/皮肤/技能书 + 星光余额 + 拥有与可购状态（服务端生成 lockReason）")
    @SentinelResource("PET_QUERY")
    public ApiResponse<PetShopVO> shop(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(shopService.shop(userId));
    }

    @PostMapping("/shop/buy")
    @Operation(summary = "购买物品", description = "等级/进化/种类门槛由服务端校验；星光不足 402（mall-wish）；"
            + "重复购买 409 PET_ITEM_ALREADY_OWNED")
    @SentinelResource("PET_SHOP")
    public ApiResponse<PetInventoryItemVO> buy(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody BuyItemRequest request) {
        return ApiResponse.ok(shopService.buy(userId, request));
    }

    @GetMapping("/inventory")
    @Operation(summary = "宠物背包", description = "装备/皮肤/技能书；装备与皮肤带 equipped 标记，技能书学会后 used=true")
    @SentinelResource("PET_QUERY")
    public ApiResponse<List<PetInventoryItemVO>> inventory(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(inventoryService.inventory(userId));
    }

    @PostMapping("/inventory/equip")
    @Operation(summary = "穿戴装备", description = "同部位自动卸下旧装备；未拥有 409；等级/进化不满足 409")
    @SentinelResource("PET_SHOP")
    public ApiResponse<PetVO> equip(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody EquipItemRequest request) {
        return ApiResponse.ok(inventoryService.equip(userId, request));
    }

    @PostMapping("/inventory/unequip")
    @Operation(summary = "卸下装备", description = "按部位卸下（HAT/NECKLACE/SCARF/BACKPACK）；该部位无装备 409")
    @SentinelResource("PET_SHOP")
    public ApiResponse<PetVO> unequip(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "装备部位") @RequestParam("slot") String slot) {
        return ApiResponse.ok(inventoryService.unequip(userId, slot));
    }

    @PostMapping("/inventory/skin")
    @Operation(summary = "穿戴皮肤", description = "写入 appearance + skinCode；种类不匹配 400；同类型皮肤互斥")
    @SentinelResource("PET_SHOP")
    public ApiResponse<PetVO> wearSkin(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody WearSkinRequest request) {
        return ApiResponse.ok(inventoryService.wearSkin(userId, request));
    }

    @PostMapping("/inventory/skin/remove")
    @Operation(summary = "卸下皮肤", description = "恢复种类原生外观（幂等：未穿戴皮肤也返回最新状态）")
    @SentinelResource("PET_SHOP")
    public ApiResponse<PetVO> removeSkin(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(inventoryService.removeSkin(userId));
    }
}
