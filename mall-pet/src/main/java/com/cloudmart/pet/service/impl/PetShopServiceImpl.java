package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.wallet.PetEconomyService;
import com.cloudmart.pet.config.PetClock;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.dto.BuyItemRequest;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetEquipmentConfig;
import com.cloudmart.pet.entity.PetInventory;
import com.cloudmart.pet.entity.PetSkill;
import com.cloudmart.pet.entity.PetSkillConfig;
import com.cloudmart.pet.entity.PetSkinConfig;
import com.cloudmart.pet.enums.PetItemType;
import com.cloudmart.pet.repository.PetEquipmentConfigMapper;
import com.cloudmart.pet.repository.PetInventoryMapper;
import com.cloudmart.pet.repository.PetSkillConfigMapper;
import com.cloudmart.pet.repository.PetSkillMapper;
import com.cloudmart.pet.repository.PetSkinConfigMapper;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.service.PetShopService;
import com.cloudmart.pet.util.PetJsonUtils;
import com.cloudmart.pet.config.PetRequestContext;
import com.cloudmart.pet.wallet.impl.PetPurchaseApplicationService;
import com.cloudmart.pet.vo.PetInventoryItemVO;
import com.cloudmart.pet.vo.PetShopItemVO;
import com.cloudmart.pet.vo.PetShopVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 宠物商城实现。
 *
 * <p>购买顺序（B01）：先可校验的配置/资格校验 → 幂等扣款（operationId =
 * SHOP_BUY:user:pet:类型:编码，钱包端按业务操作键去重）→ 本地入包（uk 唯一键幂等）。
 * 扣款结果未知（超时/降级）抛 PET_SETTLEMENT_PENDING，客户端按原请求重试幂等，
 * 禁止重新生成一笔独立交易；扣款成功但进程崩溃时由恢复任务按 rewardSnapshot
 * 幂等补入包，永久无法履约则按原单号派生唯一补偿单退款。</p>
 */
@Service
@Slf4j
public class PetShopServiceImpl implements PetShopService {

    private static final String BIZ_TYPE = "SHOP_BUY";

    private final PetService petService;
    private final PetItemCatalog itemCatalog;
    private final PetEquipmentConfigMapper equipmentConfigMapper;
    private final PetSkinConfigMapper skinConfigMapper;
    private final PetSkillConfigMapper skillConfigMapper;
    private final PetInventoryMapper inventoryMapper;
    private final PetSkillMapper skillMapper;
    private final PetEconomyService economyService;
    private final com.cloudmart.pet.service.impl.PetPlayFeatureService playFeatureService;
    private final PetClock petClock;
    private final PetPurchaseApplicationService purchaseApplicationService;

    public PetShopServiceImpl(PetService petService,
                              PetItemCatalog itemCatalog,
                              PetEquipmentConfigMapper equipmentConfigMapper,
                              PetSkinConfigMapper skinConfigMapper,
                              PetSkillConfigMapper skillConfigMapper,
                              PetInventoryMapper inventoryMapper,
                              PetSkillMapper skillMapper,
                              PetEconomyService economyService,
                    com.cloudmart.pet.service.impl.PetPlayFeatureService playFeatureService,
                              PetClock petClock,
                              PetPurchaseApplicationService purchaseApplicationService) {
        this.petService = petService;
        this.itemCatalog = itemCatalog;
        this.equipmentConfigMapper = equipmentConfigMapper;
        this.skinConfigMapper = skinConfigMapper;
        this.skillConfigMapper = skillConfigMapper;
        this.inventoryMapper = inventoryMapper;
        this.skillMapper = skillMapper;
        this.economyService = economyService;
        this.playFeatureService = playFeatureService;
        this.petClock = petClock;
        this.purchaseApplicationService = purchaseApplicationService;
    }

    @Override
    public PetShopVO shop(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        Map<PetItemType, Set<String>> owned = ownedCodesByType(pet.getId());
        Set<String> learnedSkills = learnedSkillCodes(pet.getId());

        List<PetShopItemVO> items = new ArrayList<>();
        equipmentConfigMapper.selectList(new LambdaQueryWrapper<PetEquipmentConfig>()
                        .eq(PetEquipmentConfig::getEnabled, true)
                        .orderByAsc(PetEquipmentConfig::getSort))
                .forEach(config -> items.add(itemCatalog.toShopItem(config,
                        owned.getOrDefault(PetItemType.EQUIPMENT, Set.of()).contains(config.getCode()),
                        equipmentLockReason(pet, config))));
        skinConfigMapper.selectList(new LambdaQueryWrapper<PetSkinConfig>()
                        .eq(PetSkinConfig::getEnabled, true)
                        .orderByAsc(PetSkinConfig::getSort))
                .forEach(config -> items.add(itemCatalog.toShopItem(config,
                        owned.getOrDefault(PetItemType.SKIN, Set.of()).contains(config.getCode()),
                        skinLockReason(pet, config))));
        skillConfigMapper.selectList(new LambdaQueryWrapper<PetSkillConfig>()
                        .eq(PetSkillConfig::getEnabled, true)
                        .orderByAsc(PetSkillConfig::getSort))
                .forEach(config -> items.add(itemCatalog.toShopItem(config,
                        learnedSkills.contains(config.getCode())
                                || owned.getOrDefault(PetItemType.SKILL_BOOK, Set.of()).contains(config.getCode()),
                        skillLockReason(pet, config))));
        // F1：食物道具上架（配置表，可重复购买堆叠入包）
        for (PetItemCatalog.FoodItem food : itemCatalog.listFoods()) {
            items.add(new PetShopItemVO(PetItemType.FOOD.name(), food.code(), food.name(),
                    food.description(), food.icon(), "COMMON", food.priceStarlight(),
                    null, null, null, null, null, null, null,
                    0, 0, 0, 0, 0,
                    1, 0,
                    false, true, null));
        }
        // 余额币种随钱包模式切换（PET=宠物币 / LEGACY=社区星光），字段名不再绑定币种
        String currency = economyService.mode() == com.cloudmart.pet.wallet.PetEconomyService.Mode.PET ? "PET_COIN" : "STARLIGHT";
        return new PetShopVO(balanceQuietly(userId), currency, items);
    }

    /**
     * R02 购买主链收口：旧入口委托 {@link PetPurchaseApplicationService} 统一编排
     * （意图认领→订单→钱包扣款→交付→幂等终态同事务），本类不再直接扣款/入包，
     * 只做兼容结果组装（按订单归属读背包行）。无请求键 400（PET_REQUEST_KEY_INVALID）。
     * 本方法不加事务——购买服务自身是事务边界，禁止包在更大的事务里。
     */
    @Override
    public PetInventoryItemVO buy(Long userId, BuyItemRequest request) {
        if (PetItemType.FURNITURE.name().equals(request.itemType())) {
            // 三期家具走家园商城（/home/furniture/buy）：这里显式拒绝，避免前端走错入口默默失败
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "家具请到家园商城购买哦");
        }
        PetPurchaseApplicationService.PurchaseResult result = purchaseApplicationService.purchase(
                userId, request.petId(), request.itemType(), request.itemCode(),
                PetRequestContext.idempotencyKey(), request.expectedConfigVersion());
        // R02/§8.2：同键重放返回原拒绝（errorCode 非空=存储的拒绝终态）——
        // 真实环境发现：拒绝重放继续组装 VO 会误报"结算中"503，与首次 400 不一致
        if (result.errorCode() != null) {
            throw new BusinessException(result.errorCode(), "购买未成功：" + result.errorCode());
        }
        PetInventory item = inventoryMapper.selectOne(new LambdaQueryWrapper<PetInventory>()
                .eq(PetInventory::getPetId, result.petId())
                .eq(PetInventory::getItemType, request.itemType())
                .eq(PetInventory::getItemCode, request.itemCode())
                .last("LIMIT 1"));
        if (item == null) {
            // 扣款/交付事实已提交但背包行不可读：不该发生（同事务），显式失败禁止伪装成功
            throw new BusinessException(PetErrorCodes.PET_SETTLEMENT_PENDING,
                    "购买已受理，背包同步稍后完成，请稍后刷新查看");
        }
        boolean learned = PetItemType.SKILL_BOOK.name().equals(request.itemType())
                && learnedSkillCodes(result.petId()).contains(request.itemCode());
        return itemCatalog.toInventoryVo(item, learned);
    }

    private String equipmentLockReason(Pet pet, PetEquipmentConfig config) {
        int level = config.getRequiredLevel() != null ? config.getRequiredLevel() : 1;
        if (pet.getLevel() < level) {
            return "需要 Lv." + level;
        }
        int stage = config.getRequiredEvolutionStage() != null ? config.getRequiredEvolutionStage() : 0;
        if ((pet.getEvolutionStage() != null ? pet.getEvolutionStage() : 0) < stage) {
            return "需要进化 " + stage + " 阶";
        }
        return null;
    }

    private String skinLockReason(Pet pet, PetSkinConfig config) {
        if (config.getSpecies() != null && !config.getSpecies().isBlank()
                && !config.getSpecies().equals(pet.getSpecies())) {
            return "限定 " + config.getSpecies() + " 种类";
        }
        return equipmentStageReason(pet, config.getRequiredLevel(), config.getRequiredEvolutionStage());
    }

    private String skillLockReason(Pet pet, PetSkillConfig config) {
        return equipmentStageReason(pet, config.getRequiredLevel(), 0);
    }

    private String equipmentStageReason(Pet pet, Integer requiredLevel, Integer requiredEvolutionStage) {
        int level = requiredLevel != null ? requiredLevel : 1;
        if (pet.getLevel() < level) {
            return "需要 Lv." + level;
        }
        int stage = requiredEvolutionStage != null ? requiredEvolutionStage : 0;
        if ((pet.getEvolutionStage() != null ? pet.getEvolutionStage() : 0) < stage) {
            return "需要进化 " + stage + " 阶";
        }
        return null;
    }

    /** 按 (petId, itemType, itemCode) 分型聚合拥有集合（B12：装备/皮肤/技能书编码空间独立） */
    private Map<PetItemType, Set<String>> ownedCodesByType(Long petId) {
        Map<PetItemType, Set<String>> owned = new HashMap<>();
        inventoryMapper.selectList(new LambdaQueryWrapper<PetInventory>()
                        .eq(PetInventory::getPetId, petId))
                .forEach(item -> owned
                        .computeIfAbsent(PetItemType.valueOf(item.getItemType()), type -> new HashSet<>())
                        .add(item.getItemCode()));
        return owned;
    }

    private Set<String> learnedSkillCodes(Long petId) {
        Set<String> codes = new HashSet<>();
        skillMapper.selectList(new LambdaQueryWrapper<PetSkill>()
                        .eq(PetSkill::getPetId, petId))
                .forEach(skill -> codes.add(skill.getSkillCode()));
        return codes;
    }

    /** 余额查询：展示型数据 Fail-Open（null=前端隐藏余额，不阻断商城浏览） */
    private Long balanceQuietly(Long userId) {
        try {
            return economyService.balanceOf(userId);
        } catch (Exception e) {
            log.warn("星光余额查询降级（Fail-Open）: userId={}", userId, e);
            return null;
        }
    }


    /**
     * 恢复任务回调（B01）：钱包已扣款但本地入包未落地时，按 rewardSnapshot 幂等补入包。
     * 已拥有（DuplicateKey/存在查询）视为已履约。
     */
    /** 恢复任务堆叠履约（F1）：无条件 quantity+1（insert 1 冲突转 +1） */

}
