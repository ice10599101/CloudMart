package com.cloudmart.pet.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetInventory;
import com.cloudmart.pet.entity.PetWalletAccount;
import com.cloudmart.pet.repository.PetInventoryMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.service.impl.PetConfigGovernanceService;
import com.cloudmart.pet.wallet.PetWalletAdjustmentService;
import com.cloudmart.pet.wallet.PetWalletQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户宠物查询与运营工具（F5，客服工单支撑）。
 *
 * <p>走既有 INTERNAL 鉴权 + mall-admin 代理模式（SEC-01）：查询仅客服视角只读；
 * 数值调整强制白名单字段 + 幅度上限（单次 |delta| ≤ 10000）+ 理由必填 +
 * {@code governance.snapshotAndRecord} 版本快照留痕；钱包补偿复用 W04 调账审批流
 * （申请与审批必须为不同管理员，本接口只产生申请单）。</p>
 */
@RestController
@RequestMapping("/admin/users")
@Tag(name = "宠物管理·用户运营工具", description = "按用户查宠物、数值调整（审计）、钱包补偿申请")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('INTERNAL')")
public class AdminPetUserController {

    /** 单次数值调整幅度上限（F5：危险操作加幅，防误操作破坏经济） */
    private static final int MAX_ABS_DELTA = 10000;

    /** 可调字段 → 上下界（min/max；null 表示用列语义的动态上限如 max_hp） */
    private static final Map<String, int[]> ADJUSTABLE_FIELDS = Map.of(
            "hunger", new int[]{0, 100},
            "happiness", new int[]{0, 100},
            "energy", new int[]{0, 100},
            "cleanliness", new int[]{0, 100},
            "strength", new int[]{1, 999},
            "intelligence", new int[]{1, 999},
            "agility", new int[]{1, 999},
            "charm", new int[]{1, 999});

    private static final Set<String> SPECIAL_FIELDS = Set.of("exp", "hp");

    private final PetMapper petMapper;
    private final PetInventoryMapper inventoryMapper;
    private final PetWalletQueryService walletQueryService;
    private final PetWalletAdjustmentService adjustmentService;
    private final PetConfigGovernanceService governance;

    /** 客服视角的宠物全貌条目 */
    @lombok.Builder
    public record AdminUserPetItem(
            Long petId, Long userId, String name, String species, String gender,
            int level, int exp, String growthStage, Integer evolutionStage, String skinCode,
            int hp, int maxHp, int hunger, int happiness, int energy, int cleanliness,
            int strength, int intelligence, int agility, int charm,
            String status, Boolean isActive, Boolean isPublic,
            List<InventoryLine> inventorySummary, Long walletBalance, String walletStatus) {

        /** 背包摘要行（type/code/名称/数量） */
        public record InventoryLine(String itemType, String itemCode, Integer quantity) {
        }
    }

    @GetMapping("/{userId}/pets")
    @Operation(summary = "用户宠物全貌", description = "该用户全部宠物（状态/等级/属性/背包摘要/钱包余额）；客服工单查询用")
    public ApiResponse<List<AdminUserPetItem>> userPets(
            @Parameter(description = "用户 ID") @PathVariable("userId") Long userId) {
        List<Pet> pets = petMapper.selectList(new LambdaQueryWrapper<Pet>()
                .eq(Pet::getUserId, userId)
                .orderByDesc(Pet::getIsActive)
                .orderByAsc(Pet::getId));
        PetWalletAccount wallet = walletBalanceQuietly(userId);
        List<AdminUserPetItem> items = pets.stream().map(pet -> AdminUserPetItem.builder()
                .petId(pet.getId()).userId(pet.getUserId())
                .name(pet.getName()).species(pet.getSpecies()).gender(pet.getGender())
                .level(pet.getLevel()).exp(pet.getExp()).growthStage(pet.getGrowthStage())
                .evolutionStage(pet.getEvolutionStage()).skinCode(pet.getSkinCode())
                .hp(pet.getHp()).maxHp(pet.getMaxHp())
                .hunger(pet.getHunger()).happiness(pet.getHappiness())
                .energy(pet.getEnergy()).cleanliness(pet.getCleanliness())
                .strength(pet.getStrength()).intelligence(pet.getIntelligence())
                .agility(pet.getAgility()).charm(pet.getCharm())
                .status(pet.getStatus()).isActive(pet.getIsActive()).isPublic(pet.getIsPublic())
                .inventorySummary(inventorySummary(pet.getId()))
                .walletBalance(wallet != null ? wallet.getBalance() : null)
                .walletStatus(wallet != null ? wallet.getStatus() : null)
                .build()).toList();
        return ApiResponse.ok(items);
    }

    /** 数值调整请求 */
    public record AdjustPetStateRequest(String field, Integer delta, @NotBlank String reason) {
    }

    @PostMapping("/{userId}/pets/{petId}/adjust")
    @Operation(summary = "宠物数值调整（审计）", description = "field 白名单：exp/hp/hunger/happiness/energy/cleanliness/strength/intelligence/agility/charm；" +
            "单次 |delta| ≤ 10000；reason 必填；写后快照留痕（pet_config_version）")
    public ApiResponse<Pet> adjust(
            @Parameter(description = "用户 ID") @PathVariable("userId") Long userId,
            @Parameter(description = "宠物 ID") @PathVariable("petId") Long petId,
            @Valid @RequestBody AdjustPetStateRequest request,
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId) {
        String field = request.field() != null ? request.field().strip().toLowerCase() : "";
        if (!ADJUSTABLE_FIELDS.containsKey(field) && !SPECIAL_FIELDS.contains(field)) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR,
                    "field 必须是 " + ADJUSTABLE_FIELDS.keySet() + " 或 exp/hp");
        }
        int delta = request.delta() != null ? request.delta() : 0;
        if (delta == 0 || Math.abs(delta) > MAX_ABS_DELTA) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR,
                    "delta 非零且单次幅度不超过 " + MAX_ABS_DELTA);
        }
        if (request.reason() == null || request.reason().isBlank() || request.reason().length() > 200) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "调整理由必填且不超过 200 字");
        }
        Pet pet = requireOwnedPet(userId, petId);

        // 条件更新：列内 clamp（不越界）、版本前进（乐观锁语义不破坏）
        LambdaUpdateWrapper<Pet> wrapper = new LambdaUpdateWrapper<Pet>()
                .setSql("version = version + 1")
                .eq(Pet::getId, petId)
                .eq(Pet::getUserId, userId);
        switch (field) {
            // exp 只补数值不触发升级结算（升级由正常玩法经验路径统一走 PetStateService）
            case "exp" -> wrapper.setSql("exp = GREATEST(0, exp + {0})", delta);
            case "hp" -> wrapper.setSql("hp = GREATEST(0, LEAST(max_hp, hp + {0}))", delta);
            case "hunger" -> wrapper.setSql("hunger = GREATEST({0}, LEAST({1}, hunger + {2}))",
                    ADJUSTABLE_FIELDS.get(field)[0], ADJUSTABLE_FIELDS.get(field)[1], delta)
                    .setSql("hunger_frac = 0");
            case "happiness" -> wrapper.setSql("happiness = GREATEST({0}, LEAST({1}, happiness + {2}))",
                    ADJUSTABLE_FIELDS.get(field)[0], ADJUSTABLE_FIELDS.get(field)[1], delta);
            case "energy" -> wrapper.setSql("energy = GREATEST({0}, LEAST({1}, energy + {2}))",
                    ADJUSTABLE_FIELDS.get(field)[0], ADJUSTABLE_FIELDS.get(field)[1], delta)
                    .setSql("energy_frac = 0");
            case "cleanliness" -> wrapper.setSql("cleanliness = GREATEST({0}, LEAST({1}, cleanliness + {2}))",
                    ADJUSTABLE_FIELDS.get(field)[0], ADJUSTABLE_FIELDS.get(field)[1], delta)
                    .setSql("cleanliness_frac = 0");
            default -> wrapper.setSql(field + " = GREATEST({0}, LEAST({1}, " + field + " + {2}))",
                    ADJUSTABLE_FIELDS.get(field)[0], ADJUSTABLE_FIELDS.get(field)[1], delta);
        }
        int updated = petMapper.update(null, wrapper);
        if (updated == 0) {
            throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT, "宠物状态被并发修改，请稍后重试");
        }
        Pet latest = petMapper.selectById(petId);
        // F5：快照审计（操作者从 SecurityContext 已验签声明读取，P0-3 口径）
        governance.snapshotAndRecord("pet", petId, PetConfigGovernanceService.currentOperator());
        log.info("[F5] 宠物数值调整: petId={}, field={}, delta={}, reason={}, adminUserId={}",
                petId, field, delta, request.reason(), adminUserId);
        return ApiResponse.ok(latest);
    }

    /** 钱包补偿申请请求 */
    public record CompensationRequest(@NotNull Long delta, @NotBlank String reason, String ticketNo) {
    }

    @PostMapping("/{userId}/compensation")
    @Operation(summary = "钱包补偿申请", description = "复用 W04 调账审批流（产生 PENDING 申请单，须另一管理员审批入账）；delta 带符号非 0")
    public ApiResponse<Object> compensation(
            @Parameter(description = "用户 ID") @PathVariable("userId") Long userId,
            @Valid @RequestBody CompensationRequest request,
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId) {
        long delta = request.delta() != null ? request.delta() : 0;
        if (delta == 0 || Math.abs(delta) > MAX_ABS_DELTA) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR,
                    "delta 非零且单次幅度不超过 " + MAX_ABS_DELTA);
        }
        return ApiResponse.ok(adjustmentService.apply(userId, delta, request.reason(),
                request.ticketNo(), adminUserId));
    }

    // ---------------- 内部 ----------------

    private Pet requireOwnedPet(Long userId, Long petId) {
        Pet pet = petMapper.selectById(petId);
        if (pet == null || !userId.equals(pet.getUserId())) {
            throw new BusinessException(PetErrorCodes.PET_NOT_OWNER, "该宠物不属于此用户");
        }
        return pet;
    }

    private List<AdminUserPetItem.InventoryLine> inventorySummary(Long petId) {
        return inventoryMapper.selectList(new LambdaQueryWrapper<PetInventory>()
                        .eq(PetInventory::getPetId, petId)
                        .gt(PetInventory::getQuantity, 0)
                        .orderByDesc(PetInventory::getQuantity)
                        .last("LIMIT 30"))
                .stream()
                .map(item -> new AdminUserPetItem.InventoryLine(
                        item.getItemType(), item.getItemCode(), item.getQuantity()))
                .toList();
    }

    /** 钱包余额（Fail-Open：查询异常返回未知状态，不阻断宠物信息展示） */
    private PetWalletAccount walletBalanceQuietly(Long userId) {
        try {
            return walletQueryService.getWallet(userId);
        } catch (Exception e) {
            log.warn("客服查询钱包余额降级: userId={}", userId, e);
            return null;
        }
    }
}
