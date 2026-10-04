package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.wallet.PetEconomyService;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.dto.BuyItemRequest;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetInventory;
import com.cloudmart.pet.repository.PetEquipmentConfigMapper;
import com.cloudmart.pet.repository.PetInventoryMapper;
import com.cloudmart.pet.repository.PetSkillConfigMapper;
import com.cloudmart.pet.repository.PetSkinConfigMapper;
import com.cloudmart.pet.repository.PetSkillMapper;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.wallet.impl.PetPurchaseApplicationService;
import com.cloudmart.pet.vo.PetInventoryItemVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R02 商城门面测试：buy 只做统一购买服务委托 + 兼容结果组装，
 * 扣款/交付/幂等在 PetPurchaseApplicationService 内编排（另有专测）。
 * 门槛校验（等级/进化/种类/上下架）已上收到 DefaultPetPurchaseCatalog。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PetShopServiceImpl 门面测试（R02）")
class PetShopServiceImplTest {

    @Mock
    private PetService petService;
    @Mock
    private PetItemCatalog itemCatalog;
    @Mock
    private PetEquipmentConfigMapper equipmentConfigMapper;
    @Mock
    private PetSkinConfigMapper skinConfigMapper;
    @Mock
    private PetSkillConfigMapper skillConfigMapper;
    @Mock
    private PetInventoryMapper inventoryMapper;
    @Mock
    private PetSkillMapper skillMapper;
    @Mock
    private PetPurchaseApplicationService purchaseApplicationService;

    private PetShopServiceImpl shopService;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PetInventory.class);
    }

    @BeforeEach
    void setUp() {
        shopService = new PetShopServiceImpl(petService, itemCatalog, equipmentConfigMapper, skinConfigMapper,
                skillConfigMapper, inventoryMapper, skillMapper,
                org.mockito.Mockito.mock(PetEconomyService.class),
                org.mockito.Mockito.mock(com.cloudmart.pet.config.PetClock.class),
                purchaseApplicationService);
        lenient().when(petService.requireOwnedPet(100L)).thenReturn(pet());
        lenient().when(skillMapper.selectList(any())).thenReturn(List.of());
        // 拦截器在真实请求中捕获 Idempotency-Key；测试里手动放置并清理
        com.cloudmart.pet.config.PetRequestContext.setIdempotencyKey("req-key-16-chars-ok");
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        com.cloudmart.pet.config.PetRequestContext.clear();
    }

    @Test
    @DisplayName("R02 购买委托：携带幂等键/目标宠物/版本调用统一购买服务，按归属宠物组装背包 VO")
    void buyDelegatesToPurchaseApplicationService() {
        PetInventory row = inventory();
        var result = new PetPurchaseApplicationService.PurchaseResult(
                "2040000000000000001", "pw_x", "wt_1", 80L, 1L, "FOOD", "apple", List.of("apple"), false, null);
        when(purchaseApplicationService.purchase(100L, 1L, "FOOD", "apple", "req-key-16-chars-ok", null))
                .thenReturn(result);
        when(inventoryMapper.selectOne(any())).thenReturn(row);
        PetInventoryItemVO vo = new PetInventoryItemVO("FOOD", "apple", "苹果", "", "🍎",
                "COMMON", null, null, null, null, null, 0, 0, 0, 0, 0, false, 1, false, null);
        when(itemCatalog.toInventoryVo(any(), eq(false))).thenReturn(vo);

        PetInventoryItemVO out = shopService.buy(100L,
                new BuyItemRequest("FOOD", "apple", 1L, null));

        assertThat(out.code()).isEqualTo("apple");
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PetInventory>> captor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(inventoryMapper).selectOne(captor.capture());
        assertThat(captor.getValue()).isNotNull();
    }

    @Test
    @DisplayName("R02 缺幂等键：统一购买服务抛 PET_REQUEST_KEY_INVALID 时门面不吞（400）")
    void missingRequestKeyPropagates() {
        when(purchaseApplicationService.purchase(eq(100L), any(), eq("FOOD"), eq("apple"), any(), any()))
                .thenThrow(new BusinessException(PetErrorCodes.PET_REQUEST_KEY_INVALID, "缺少有效幂等键"));

        assertThatThrownBy(() -> shopService.buy(100L, new BuyItemRequest("FOOD", "apple", 1L, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(PetErrorCodes.PET_REQUEST_KEY_INVALID);
    }

    @Test
    @DisplayName("家具购买走家园商城：商城入口显式拒绝，不进购买服务")
    void furnitureRedirectedToHomeShop() {
        assertThatThrownBy(() -> shopService.buy(100L, new BuyItemRequest("FURNITURE", "rug", 1L, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(PetErrorCodes.PET_VALIDATION_ERROR);
        verify(purchaseApplicationService, org.mockito.Mockito.never()).purchase(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("R02 拒绝终态重放：errorCode 非空 → 门面转抛同码拒绝（不得继续组装 VO 误报 503）")
    void replayedRejectionPropagates() {
        var rejected = new PetPurchaseApplicationService.PurchaseResult(
                null, "pw_x", null, null, 1L, "FOOD", "apple", List.of(), false,
                "PET_WALLET_INSUFFICIENT");
        when(purchaseApplicationService.purchase(eq(100L), any(), any(), any(), any(), any()))
                .thenReturn(rejected);

        assertThatThrownBy(() -> shopService.buy(100L, new BuyItemRequest("FOOD", "apple", 1L, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo("PET_WALLET_INSUFFICIENT");
    }

    @Test
    @DisplayName("R02 防伪装成功：订单成功但背包行不可读 → PET_SETTLEMENT_PENDING 而非返回 null")
    void missingInventoryRowFailsLoudly() {
        var result = new PetPurchaseApplicationService.PurchaseResult(
                "2040000000000000001", "pw_x", "wt_1", 80L, 1L, "FOOD", "apple", List.of("apple"), false, null);
        when(purchaseApplicationService.purchase(eq(100L), any(), any(), any(), any(), any()))
                .thenReturn(result);
        when(inventoryMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> shopService.buy(100L, new BuyItemRequest("FOOD", "apple", 1L, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(PetErrorCodes.PET_SETTLEMENT_PENDING);
    }

    private Pet pet() {
        Pet pet = new Pet();
        pet.setId(1L);
        pet.setUserId(100L);
        pet.setName("小橘");
        pet.setSpecies("STRAWBERRY");
        pet.setLevel(5);
        pet.setMaxHp(100);
        pet.setHp(80);
        pet.setStrength(5);
        pet.setIntelligence(5);
        pet.setAgility(5);
        pet.setCharm(5);
        pet.setEvolutionStage(0);
        return pet;
    }

    private PetInventory inventory() {
        PetInventory item = new PetInventory();
        item.setPetId(1L);
        item.setUserId(100L);
        item.setItemType("FOOD");
        item.setItemCode("apple");
        item.setQuantity(1);
        item.setEquipped(false);
        return item;
    }
}
