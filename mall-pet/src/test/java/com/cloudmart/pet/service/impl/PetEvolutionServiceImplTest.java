package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.wallet.PetEconomyService;
import com.cloudmart.pet.wallet.PetRequestDedupService;
import com.cloudmart.pet.wallet.impl.PetRequestDedupServiceImpl;
import com.cloudmart.pet.repository.PetRequestDedupMapper;
import com.cloudmart.pet.entity.PetRequestDedup;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetActivity;
import com.cloudmart.pet.entity.PetEvolutionConfig;
import com.cloudmart.pet.entity.PetInventory;
import com.cloudmart.pet.mq.PetEventProducer;
import com.cloudmart.pet.repository.PetActivityMapper;
import com.cloudmart.pet.repository.PetEvolutionConfigMapper;
import com.cloudmart.pet.repository.PetInventoryMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.service.PetAchievementService;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.util.PetJsonUtils;
import com.cloudmart.pet.vo.PetEvolutionVO;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 宠物进化测试：等级门槛、满阶拒绝、成功后属性/阶段/皮肤入包、星光不足的只读判定。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PetEvolutionServiceImpl 单元测试")
class PetEvolutionServiceImplTest {

    @Mock
    private PetService petService;
    @Mock
    private PetEvolutionConfigMapper evolutionConfigMapper;
    @Mock
    private PetMapper petMapper;
    @Mock
    private PetInventoryMapper inventoryMapper;
    @Mock
    private PetActivityMapper activityMapper;
    @Mock
    private PetEconomyService economyService;
    @Mock
    private PetAchievementService achievementService;
    @Mock
    private PetEventProducer eventProducer;
    @org.mockito.Mock
    private com.cloudmart.pet.service.impl.PetPlayFeatureService playFeatureService;
    @org.mockito.Mock
    private PetRequestDedupMapper dedupMapper;


    private PetEvolutionServiceImpl evolutionService;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PetActivity.class);
        TableInfoHelper.initTableInfo(assistant, PetInventory.class);
        TableInfoHelper.initTableInfo(assistant, Pet.class);
        TableInfoHelper.initTableInfo(assistant, PetRequestDedup.class);
    }

    @BeforeEach
    void setUp() {
        economyService = org.mockito.Mockito.mock(PetEconomyService.class);
                org.mockito.Mockito.when(economyService.spend(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(Object[].class)))
                .thenReturn(new PetEconomyService.WalletSettlement("COMPLETED", 0L, 1000L, false, null));
        com.cloudmart.pet.config.PetClock petClock = org.mockito.Mockito.mock(com.cloudmart.pet.config.PetClock.class);
        org.mockito.Mockito.when(petClock.nowUtc())
                .thenAnswer(inv -> java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
        org.mockito.Mockito.lenient().when(petMapper.updateById(org.mockito.ArgumentMatchers.any(com.cloudmart.pet.entity.Pet.class))).thenReturn(1);
        org.mockito.Mockito.lenient().when(petMapper.update(any(), any())).thenReturn(1);
        // R28：真实去重桩（mock mapper）+ 直通事务模板；拦截器在真实请求中捕获幂等键，测试手动放置
        PetRequestDedupService dedupService = new PetRequestDedupServiceImpl(dedupMapper);
        lenient().when(dedupMapper.insert(any(PetRequestDedup.class))).thenReturn(1);
        lenient().when(dedupMapper.update(any(), any())).thenReturn(1);
        org.springframework.transaction.support.TransactionTemplate txTemplate =
                org.mockito.Mockito.mock(org.springframework.transaction.support.TransactionTemplate.class);
        org.mockito.Mockito.when(txTemplate.execute(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> ((org.springframework.transaction.support.TransactionCallback<?>) inv.getArgument(0))
                        .doInTransaction(org.mockito.Mockito.mock(org.springframework.transaction.TransactionStatus.class)));
        evolutionService = new PetEvolutionServiceImpl(petService, evolutionConfigMapper, petMapper,
                inventoryMapper, activityMapper, achievementService, economyService,
                playFeatureService, org.mockito.Mockito.mock(PetOutboxService.class), petClock,
                dedupService, txTemplate);
        lenient().when(inventoryMapper.insert(any(PetInventory.class))).thenReturn(1);
        lenient().when(activityMapper.insert(any(PetActivity.class))).thenReturn(1);
        com.cloudmart.pet.config.PetRequestContext.setIdempotencyKey("intent-key-evolve-0001");
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        com.cloudmart.pet.config.PetRequestContext.clear();
    }

    @Test
    @DisplayName("R28 进化成功：条件更新推进阶段 + 冻结 fromStage 扣款键 + 皮肤入包 + 成就评估")
    void evolveAppliesBonusesAndSpends() {
        Pet pet = pet(10, 0);
        when(petMapper.selectById(1L)).thenReturn(pet);
        when(evolutionConfigMapper.selectList(any())).thenReturn(List.of(evolve1()));

        PetEvolutionVO result = evolutionService.evolve(100L, 1L, null);

        // 阶段推进走条件更新（WHERE evolution_stage=fromStage），不再全实体覆盖
        verify(petMapper).update(any(), any());
        assertThat(result.currentStage()).isEqualTo(1);
        // 扣款键冻结 fromStage+stageTo（重试收敛原单，不会把重试解释为下一阶段）
        org.mockito.Mockito.verify(economyService).spend(org.mockito.ArgumentMatchers.eq(100L), org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq("EVOLVE"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(600L), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(Object[].class));
        verify(inventoryMapper).insert(any(PetInventory.class));
        verify(achievementService).evaluate(pet, PetAchievementService.Event.EVOLUTION);
    }

    @Test
    @DisplayName("R28 同键重放：返回原进化结果，不重读阶段、不重新扣款")
    void evolveSameKeyReplaysStoredResult() {
        Pet pet = pet(10, 0);
        when(petMapper.selectById(1L)).thenReturn(pet);
        when(evolutionConfigMapper.selectList(any())).thenReturn(List.of(evolve1()));

        PetEvolutionVO first = evolutionService.evolve(100L, 1L, null);

        // 第二次同键：dedup 行已存在（uk 冲突），返回首次终态快照——
        // 即使钱包等级余额满足下一阶段，重放也绝不再次选择 nextConfig
        org.mockito.Mockito.reset(petMapper);
        when(petMapper.selectById(1L)).thenReturn(pet(10, 0));
        when(dedupMapper.insert(any(PetRequestDedup.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("uk"));
        PetRequestDedupService dedupServiceBean = new PetRequestDedupServiceImpl(dedupMapper);
        PetRequestDedup existing = new PetRequestDedup();
        existing.setStatus("COMPLETED");
        existing.setPayloadHash(dedupServiceBean.canonicalHash(100L, 1L, "EVOLVE", ""));
        existing.setResponseJson(PetJsonUtils.toJson(java.util.Map.of("type", "SUCCEEDED", "vo", first)));
        when(dedupMapper.selectOne(any())).thenReturn(existing);

        PetEvolutionVO replay = evolutionService.evolve(100L, 1L, null);

        assertThat(replay.currentStage()).isEqualTo(1);
        // 重放直接返回存储结果：不再触发扣款与阶段推进
        org.mockito.Mockito.verify(economyService, org.mockito.Mockito.times(1)).spend(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(Object[].class));
    }

    @Test
    @DisplayName("R28 阶段不符：expectedFromStage 与当前不符 → 409 PET_STATE_CONFLICT")
    void evolveStageMismatchRejected() {
        when(petMapper.selectById(1L)).thenReturn(pet(10, 1));

        assertThatThrownBy(() -> evolutionService.evolve(100L, 1L, 0))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(PetErrorCodes.PET_STATE_CONFLICT);
    }

    @Test
    @DisplayName("等级不足：409 PET_LEVEL_REQUIRED，不扣星光")
    void levelGateBlocksEvolution() {
        Pet pet = pet(3, 0);
        when(petMapper.selectById(1L)).thenReturn(pet);
        when(evolutionConfigMapper.selectList(any())).thenReturn(List.of(evolve1()));

        assertThatThrownBy(() -> evolutionService.evolve(100L, 1L, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(PetErrorCodes.PET_LEVEL_REQUIRED);
        verify(economyService, org.mockito.Mockito.never()).spend(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(Object[].class));
    }

    @Test
    @DisplayName("已到最高阶：409 PET_EVOLUTION_MAX")
    void maxStageRejected() {
        Pet pet = pet(30, 2);
        when(petMapper.selectById(1L)).thenReturn(pet);
        when(evolutionConfigMapper.selectList(any())).thenReturn(List.of(evolve1()));

        assertThatThrownBy(() -> evolutionService.evolve(100L, 1L, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(PetErrorCodes.PET_EVOLUTION_MAX);
    }

    @Test
    @DisplayName("只读状态：星光不足 → canEvolve=false 且给出可展示原因")
    void statusReportsInsufficientStarlight() {
        Pet pet = pet(10, 0);
        when(petService.requireOwnedPet(100L)).thenReturn(pet);
        when(evolutionConfigMapper.selectList(any())).thenReturn(List.of(evolve1()));

        PetEvolutionVO status = evolutionService.status(100L);

        assertThat(status.canEvolve()).isFalse();
        assertThat(status.lockReason()).contains("星光不足");
    }

    @Test
    @DisplayName("只读状态：达到最高阶 → nextCode 为空且提示已满阶")
    void statusMaxStage() {
        Pet pet = pet(30, 2);
        when(petService.requireOwnedPet(100L)).thenReturn(pet);
        when(evolutionConfigMapper.selectList(any())).thenReturn(List.of(evolve1()));

        PetEvolutionVO status = evolutionService.status(100L);

        assertThat(status.nextCode()).isNull();
        assertThat(status.maxStage()).isEqualTo(1);
        assertThat(status.canEvolve()).isFalse();
    }

    private PetEvolutionConfig evolve1() {
        PetEvolutionConfig config = new PetEvolutionConfig();
        config.setCode("evolve_1");
        config.setName("初阶进化");
        config.setDescription("毛发泛起微光");
        config.setStageFrom(0);
        config.setStageTo(1);
        config.setRequiredLevel(8);
        config.setCostStarlight(600);
        config.setBonusMaxHp(25);
        config.setBonusStrength(3);
        config.setBonusIntelligence(3);
        config.setBonusAgility(3);
        config.setBonusCharm(3);
        config.setUnlockSkinCode("aurora_legend");
        config.setEnabled(true);
        return config;
    }

    private Pet pet(int level, int evolutionStage) {
        Pet pet = new Pet();
        pet.setId(1L);
        pet.setUserId(100L);
        pet.setName("小橘");
        pet.setSpecies("STRAWBERRY");
        pet.setLevel(level);
        pet.setEvolutionStage(evolutionStage);
        pet.setMaxHp(100);
        pet.setHp(90);
        pet.setStrength(5);
        pet.setIntelligence(5);
        pet.setAgility(5);
        pet.setCharm(5);
        return pet;
    }
}
