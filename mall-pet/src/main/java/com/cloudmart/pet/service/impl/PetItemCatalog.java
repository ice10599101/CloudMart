package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.pet.entity.PetEquipmentConfig;
import com.cloudmart.pet.entity.PetFoodConfig;
import com.cloudmart.pet.entity.PetFurnitureConfig;
import com.cloudmart.pet.entity.PetInventory;
import com.cloudmart.pet.entity.PetSkillConfig;
import com.cloudmart.pet.entity.PetSkinConfig;
import com.cloudmart.pet.enums.PetItemType;
import com.cloudmart.pet.repository.PetEquipmentConfigMapper;
import com.cloudmart.pet.repository.PetFoodConfigMapper;
import com.cloudmart.pet.repository.PetFurnitureConfigMapper;
import com.cloudmart.pet.repository.PetSkillConfigMapper;
import com.cloudmart.pet.repository.PetSkinConfigMapper;
import com.cloudmart.pet.vo.PetInventoryItemVO;
import com.cloudmart.pet.vo.PetShopItemVO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 宠物物品目录（装备/皮肤/技能书配置的统一装配层）。
 *
 * <p>存在意义：商城、背包、技能三个业务都会把"配置 + 拥有状态"装配成 VO；
 * 集中在这里避免三处各自维护一份字段映射（新增字段只改一处）。</p>
 */
@Component
public class PetItemCatalog {

    /** 原生外观默认色（卸下皮肤时回落到种类默认色，与前端/Cocos 调色板键一致） */
    private static final Map<String, String> SPECIES_DEFAULT_COLOR = Map.of(
            "STRAWBERRY", "pink",
            "ORANGE", "orange",
            "WATERMELON", "green",
            "BLUEBERRY", "blue",
            "DRAGONFRUIT", "pink");

    private static final String DEFAULT_COLOR = "orange";
    private static final String DEFAULT_ACCESSORY = "none";

    /**
     * 食物道具（F1 配置化）：配置存 {@code pet_food_config}（管理端可编辑，服务端权威），
     * 本地 60 秒 TTL 缓存定时同步；管理端保存后调 {@link #invalidateFoodCache()} 本实例即时生效。
     * 堆叠入包 {@code pet_inventory(quantity)}，喂食时条件扣减。
     */
    public record FoodItem(String code, String name, String icon, String description,
                           int priceStarlight, int hunger, int happiness, int hp) {
    }

    private static final long FOOD_CACHE_TTL_NANOS = 60L * 1_000_000_000L;
    private final PetFoodConfigMapper foodConfigMapper;
    private final java.util.concurrent.atomic.AtomicReference<CachedFoods> foodsCache =
            new java.util.concurrent.atomic.AtomicReference<>(new CachedFoods(List.of(), 0));
    private final PetEquipmentConfigMapper equipmentConfigMapper;
    private final PetSkinConfigMapper skinConfigMapper;
    private final PetSkillConfigMapper skillConfigMapper;
    private final PetFurnitureConfigMapper furnitureConfigMapper;

    private record CachedFoods(List<FoodItem> foods, long expiresAtNanos) {
    }

    /** 实例构造后由 Spring 注入（食物 Mapper 为新增依赖） */
    public PetItemCatalog(PetEquipmentConfigMapper equipmentConfigMapper,
                          PetSkinConfigMapper skinConfigMapper,
                          PetSkillConfigMapper skillConfigMapper,
                          PetFurnitureConfigMapper furnitureConfigMapper,
                          PetFoodConfigMapper foodConfigMapper) {
        this.equipmentConfigMapper = equipmentConfigMapper;
        this.skinConfigMapper = skinConfigMapper;
        this.skillConfigMapper = skillConfigMapper;
        this.furnitureConfigMapper = furnitureConfigMapper;
        this.foodConfigMapper = foodConfigMapper;
    }

    /** 上架食物列表（商城展示；TTL 缓存，查询失败回落最近一次成功快照，首次失败返回空） */
    public List<FoodItem> listFoods() {
        return foodsSnapshot().foods();
    }

    /** 按编码取食物（喂食接口；下架/不存在返回 empty） */
    public Optional<FoodItem> food(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return foodsSnapshot().foods().stream().filter(f -> f.code().equals(code)).findFirst();
    }

    /** 管理端保存后调用：本实例下次访问即拉新（其他实例靠 60 秒 TTL 收敛） */
    public void invalidateFoodCache() {
        foodsCache.set(new CachedFoods(List.of(), 0));
    }

    private CachedFoods foodsSnapshot() {
        CachedFoods cached = foodsCache.get();
        if (cached.expiresAtNanos() > System.nanoTime() && !cached.foods().isEmpty()) {
            return cached;
        }
        try {
            List<FoodItem> foods = foodConfigMapper.selectList(
                            new LambdaQueryWrapper<PetFoodConfig>()
                                    .eq(PetFoodConfig::getEnabled, 1)
                                    .orderByAsc(PetFoodConfig::getSort))
                    .stream()
                    .map(config -> new FoodItem(config.getCode(), config.getName(),
                            config.getIcon() != null ? config.getIcon() : "🍎",
                            config.getDescription() != null ? config.getDescription() : "",
                            config.getPriceStarlight() != null ? config.getPriceStarlight() : 0,
                            config.getHunger() != null ? config.getHunger() : 0,
                            config.getHappiness() != null ? config.getHappiness() : 0,
                            config.getHp() != null ? config.getHp() : 0))
                    .toList();
            CachedFoods fresh = new CachedFoods(foods, System.nanoTime() + FOOD_CACHE_TTL_NANOS);
            foodsCache.set(fresh);
            return fresh;
        } catch (Exception e) {
            // Fail-Open：DB 抖动沿用旧快照（与模块内展示型数据降级同风格）
            return cached;
        }
    }

    /** 家具配置（三期家园）：不存在返回空 */
    public Optional<PetFurnitureConfig> furniture(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(furnitureConfigMapper.selectOne(new LambdaQueryWrapper<PetFurnitureConfig>()
                .eq(PetFurnitureConfig::getCode, code)
                .last("LIMIT 1")));
    }

    public Optional<PetEquipmentConfig> equipment(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(equipmentConfigMapper.selectOne(new LambdaQueryWrapper<PetEquipmentConfig>()
                .eq(PetEquipmentConfig::getCode, code)
                .last("LIMIT 1")));
    }

    public Optional<PetSkinConfig> skin(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(skinConfigMapper.selectOne(new LambdaQueryWrapper<PetSkinConfig>()
                .eq(PetSkinConfig::getCode, code)
                .last("LIMIT 1")));
    }

    public Optional<PetSkillConfig> skill(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(skillConfigMapper.selectOne(new LambdaQueryWrapper<PetSkillConfig>()
                .eq(PetSkillConfig::getCode, code)
                .last("LIMIT 1")));
    }

    /** 原生外观默认色（种类未知时回落 orange） */
    public static String defaultColorFor(String species) {
        return species != null ? SPECIES_DEFAULT_COLOR.getOrDefault(species, DEFAULT_COLOR) : DEFAULT_COLOR;
    }

    public static String defaultAccessory() {
        return DEFAULT_ACCESSORY;
    }

    /**
     * 背包物品 VO；配置已被后台删除时返回占位（保留背包记录可见，避免"物品消失"）。
     * 技能书是否"已使用"由调用方传入（技能书学习后仍留背包作收藏）。
     */
    public PetInventoryItemVO toInventoryVo(PetInventory item, boolean used) {
        String type = item.getItemType();
        if (PetItemType.EQUIPMENT.name().equals(type)) {
            PetEquipmentConfig config = equipment(item.getItemCode()).orElse(null);
            return new PetInventoryItemVO(type, item.getItemCode(),
                    config != null ? config.getName() : item.getItemCode(),
                    config != null ? config.getDescription() : "",
                    config != null ? config.getIcon() : "🎀",
                    config != null ? config.getRarity() : "COMMON",
                    config != null ? config.getSlot() : item.getSlot(),
                    null, null, null, null,
                    config != null ? config.getBonusStrength() : 0,
                    config != null ? config.getBonusIntelligence() : 0,
                    config != null ? config.getBonusAgility() : 0,
                    config != null ? config.getBonusCharm() : 0,
                    config != null ? config.getBonusMaxHp() : 0,
                    Boolean.TRUE.equals(item.getEquipped()), item.getQuantity(), used, item.getAcquiredAt());
        }
        if (PetItemType.SKIN.name().equals(type)) {
            PetSkinConfig config = skin(item.getItemCode()).orElse(null);
            return new PetInventoryItemVO(type, item.getItemCode(),
                    config != null ? config.getName() : item.getItemCode(),
                    config != null ? config.getDescription() : "",
                    config != null ? config.getIcon() : "✨",
                    config != null ? config.getRarity() : "COMMON",
                    null,
                    config != null ? config.getColor() : null,
                    config != null ? config.getAccessory() : null,
                    null, null, 0, 0, 0, 0, 0,
                    Boolean.TRUE.equals(item.getEquipped()), item.getQuantity(), used, item.getAcquiredAt());
        }
        if (PetItemType.FURNITURE.name().equals(type)) {
            // 家具借用 slot 字段回传分类、effect/effectValue 回传舒适度（前端背包统一渲染）
            PetFurnitureConfig config = furniture(item.getItemCode()).orElse(null);
            return new PetInventoryItemVO(type, item.getItemCode(),
                    config != null ? config.getName() : item.getItemCode(),
                    config != null ? config.getDescription() : "",
                    config != null ? config.getIcon() : "🧸",
                    config != null ? config.getRarity() : "COMMON",
                    config != null ? config.getCategory() : item.getSlot(),
                    null, null,
                    config != null ? config.getCategory() : null,
                    java.math.BigDecimal.valueOf(
                            config != null && config.getComfort() != null ? config.getComfort() : 0),
                    0, 0, 0, 0, 0,
                    Boolean.TRUE.equals(item.getEquipped()), item.getQuantity(), used, item.getAcquiredAt());
        }
        if (PetItemType.FOOD.name().equals(type)) {
            // F1：食物目录为代码定义（无配置表行），缺失时回落编码占位
            FoodItem food = food(item.getItemCode()).orElse(null);
            return new PetInventoryItemVO(type, item.getItemCode(),
                    food != null ? food.name() : item.getItemCode(),
                    food != null ? food.description() : "",
                    food != null ? food.icon() : "🍎",
                    "COMMON", null, null, null, null, null, 0, 0, 0, 0, 0,
                    Boolean.TRUE.equals(item.getEquipped()), item.getQuantity(), used, item.getAcquiredAt());
        }
        PetSkillConfig config = skill(item.getItemCode()).orElse(null);
        return new PetInventoryItemVO(type, item.getItemCode(),
                config != null ? config.getName() : item.getItemCode(),
                config != null ? config.getDescription() : "",
                config != null ? config.getIcon() : "📖",
                "COMMON", null, null, null,
                config != null ? config.getEffect() : null,
                config != null ? config.getEffectValue() : null,
                0, 0, 0, 0, 0,
                Boolean.TRUE.equals(item.getEquipped()), item.getQuantity(), used, item.getAcquiredAt());
    }

    public PetShopItemVO toShopItem(PetEquipmentConfig config, boolean owned, String lockReason) {
        return new PetShopItemVO(PetItemType.EQUIPMENT.name(), config.getCode(), config.getName(),
                config.getDescription(), config.getIcon(), config.getRarity(), config.getPriceStarlight(),
                config.getSlot(), null, null, null, null, null, null,
                config.getBonusStrength(), config.getBonusIntelligence(), config.getBonusAgility(),
                config.getBonusCharm(), config.getBonusMaxHp(),
                config.getRequiredLevel(), config.getRequiredEvolutionStage(),
                owned, lockReason == null, lockReason);
    }

    public PetShopItemVO toShopItem(PetSkinConfig config, boolean owned, String lockReason) {
        return new PetShopItemVO(PetItemType.SKIN.name(), config.getCode(), config.getName(),
                config.getDescription(), config.getIcon(), config.getRarity(), config.getPriceStarlight(),
                null, config.getSpecies(), config.getColor(), config.getAccessory(), null, null, null,
                0, 0, 0, 0, 0,
                config.getRequiredLevel(), config.getRequiredEvolutionStage(),
                owned, lockReason == null, lockReason);
    }

    public PetShopItemVO toShopItem(PetSkillConfig config, boolean owned, String lockReason) {
        return new PetShopItemVO(PetItemType.SKILL_BOOK.name(), config.getCode(), config.getName(),
                config.getDescription(), config.getIcon(), "EPIC", config.getPriceStarlight(),
                null, null, null, null, config.getSkillType(), config.getEffect(), config.getEffectValue(),
                0, 0, 0, 0, 0,
                config.getRequiredLevel(), 0,
                owned, lockReason == null, lockReason);
    }
}
