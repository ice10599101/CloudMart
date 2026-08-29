package com.cloudmart.wish.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 虚拟资产纯函数工具（Sprint 3.6）：限量 Redis 预扣与回补、资产类型
 * 校验、支付方式校验。
 */
public final class VirtualAssetHelper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private VirtualAssetHelper() {
    }

    /**
     * 校验支付方式与资产配置一致（STARLIGHT 价格 0 → 不可星光兑换）。
     */
    public static void validatePayMethod(String payMethod, int priceStarlight, int priceRmb) {
        boolean ok = switch (payMethod) {
            case "STARLIGHT" -> priceStarlight > 0;
            case "RMB" -> priceRmb > 0;
            case "BOTH" -> priceStarlight > 0 && priceRmb > 0;
            default -> false;
        };
        if (!ok) {
            throw new IllegalArgumentException("支付方式与价格配置不一致: " + payMethod);
        }
    }

    /** 限量扣减 Redis key（DECR 原子预扣，返回余量 <0 则回补并拒绝） */
    public static String stockKey(Long assetId) {
        return "workshop:stock:" + assetId;
    }

    /** 资产类型校验 */
    public static void validateAssetType(String type) {
        if (!List.of("SKIN", "BGM", "SPECIAL_FRUIT").contains(type)) {
            throw new IllegalArgumentException("非法资产类型: " + type);
        }
    }

    /** JSON 安全解析（Fail-Open 空节点） */
    public static JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) {
            return MAPPER.createObjectNode();
        }
        try {
            return MAPPER.readTree(json);
        } catch (Exception ex) {
            return MAPPER.createObjectNode();
        }
    }
}
