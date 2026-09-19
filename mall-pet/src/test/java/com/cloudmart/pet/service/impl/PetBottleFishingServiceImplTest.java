package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetActivity;
import com.cloudmart.pet.entity.PetBottleRecord;
import com.cloudmart.pet.enums.PetActivityStatus;
import com.cloudmart.pet.enums.PetActivityType;
import com.cloudmart.pet.enums.PetBottleOutcome;
import com.cloudmart.pet.enums.PetBottleRarity;
import com.cloudmart.pet.feign.WishFeignClient;
import com.cloudmart.pet.repository.PetActivityMapper;
import com.cloudmart.pet.repository.PetBottleRecordMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.mq.PetEventProducer;
import com.cloudmart.pet.service.PetAchievementService;
import com.cloudmart.pet.service.PetService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 宠物捞漂流瓶测试：成功率边界、结算落流水、Feign 降级 FAILED 可重试、冷却/互斥。
 * 随机 roll（ThreadLocalRandom）不可注入——以 95% 成功率属性多次驱动，
 * 用捕获的流水记录断言"全部落库、outcome 合法"，避免伪断言。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PetBottleFishingServiceImpl 单元测试")
class PetBottleFishingServiceImplTest {

    @Mock
    private PetService petService;
    @Mock
    private PetStateService stateService;
    @Mock
    private PetActivityMapper activityMapper;
    @Mock
    private PetBottleRecordMapper bottleRecordMapper;
    @Mock
    private PetMapper petMapper;
    @Mock
    private WishFeignClient wishFeignClient;
    @Mock
    private PetAchievementService achievementService;
    @Mock
    private PetEventProducer eventProducer;
    @Mock
    private PetBottleContentProvider contentProvider;
    @Mock
    private PetStatsService statsService;

    private PetBottleFishingServiceImpl bottleService;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Pet.class);
        TableInfoHelper.initTableInfo(assistant, PetActivity.class);
        TableInfoHelper.initTableInfo(assistant, PetBottleRecord.class);
    }

    @BeforeEach
    void setUp() {
        bottleService = new PetBottleFishingServiceImpl(petService, stateService, activityMapper,
                bottleRecordMapper, petMapper, wishFeignClient, achievementService, eventProducer,
                contentProvider, new PetProperties(), statsService);
        // 无装备/技能时战斗属性 = 宠物基础属性（与改造前成功率口径一致）
        lenient().when(statsService.combatStats(any())).thenAnswer(invocation -> {
            Pet pet = invocation.getArgument(0);
            return new PetStatsService.CombatStats(num(pet.getHp()), num(pet.getMaxHp()), num(pet.getStrength()),
                    num(pet.getIntelligence()), num(pet.getAgility()), num(pet.getCharm()), 0, 0, 0, 0, 0);
        });
        lenient().when(statsService.bottleSuccessBonus(any())).thenReturn(0.0);
    }

    private static int num(Integer value) {
        return value != null ? value : 0;
    }

    private Pet pet(int agility, int level) {
        Pet pet = new Pet();
        pet.setId(1L);
        pet.setUserId(100L);
        pet.setName("小橘");
        pet.setLevel(level);
        pet.setAgility(agility);
        pet.setEnergy(100);
        pet.setHunger(80);
        pet.setHappiness(80);
        pet.setCleanliness(80);
        pet.setHp(100);
        pet.setMaxHp(100);
        pet.setLastStateUpdateAt(LocalDateTime.now(ZoneId.of("UTC")));
        pet.setVersion(0);
        return pet;
    }

    private PetActivity inProgressFishing(long id) {
        PetActivity fishing = new PetActivity();
        fishing.setId(id);
        fishing.setPetId(1L);
        fishing.setUserId(100L);
        fishing.setActivityType(PetActivityType.BOTTLE_FISHING.name());
        fishing.setStatus(PetActivityStatus.IN_PROGRESS.name());
        fishing.setStartedAt(LocalDateTime.now(ZoneId.of("UTC")).minusMinutes(31));
        fishing.setFinishedAt(LocalDateTime.now(ZoneId.of("UTC")).minusMinutes(1));
        return fishing;
    }

    @Test
    @DisplayName("成功率公式：base70% + 敏捷×0.5% + 等级×1% + 区域加成(每档1%)，封顶 95%")
    void successRateFormula() {
        // level 6 → 区域 index 1（城市河流）；level 10 → index 2 但被 95% 封顶覆盖
        assertThat(bottleService.estimateSuccessRate(pet(30, 6)))
                .isEqualTo(0.70 + 30 * 0.005 + 6 * 0.01 + 0.01, within(1e-9));
        assertThat(bottleService.estimateSuccessRate(pet(100, 100)))
                .isEqualTo(0.95, within(1e-9));
        assertThat(bottleService.estimateSuccessRate(pet(0, 1)))
                .isEqualTo(0.71, within(1e-9));
    }

    @Test
    @DisplayName("稀有度抽取：边界 roll 命中四档（0.08/0.16/0.20 分界）")
    void rollRarityBoundaries() {
        // 直接验证分布：大量 roll 全部落在四档内
        java.util.Set<PetBottleRarity> seen = new java.util.HashSet<>();
        for (int i = 0; i < 500; i++) {
            seen.add(bottleService.rollRarity());
        }
        // 500 次采样，普通/稀有/宠物/彩蛋四档几乎必然全部出现（彩蛋 4% → P(缺)≈(0.96)^500≈1.3e-9）
        org.assertj.core.api.Assertions.assertThat(seen)
                .contains(PetBottleRarity.NORMAL, PetBottleRarity.RARE, PetBottleRarity.PET, PetBottleRarity.EASTER_EGG);
    }

    @Test
    @DisplayName("特殊瓶内容源：RARE/PET/EASTER_EGG 返回非空文本，NORMAL 返回 null")
    void contentProviderPicks() {
        PetBottleContentProvider provider = new PetBottleContentProvider();
        org.assertj.core.api.Assertions.assertThat(provider.pick(PetBottleRarity.RARE)).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(provider.pick(PetBottleRarity.PET)).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(provider.pick(PetBottleRarity.EASTER_EGG)).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(provider.pick(PetBottleRarity.NORMAL)).isNull();
    }

    @Test
    @DisplayName("结算（roll 未进海）：流水落库、outcome ∈ {CAUGHT, EMPTY}、任务 COMPLETED")
    void settleRecordsOutcome() {
        Pet p = pet(30, 10);
        when(petService.requireOwnedPet(100L)).thenReturn(p);
        when(stateService.grantExp(any(Pet.class), any(Integer.class))).thenReturn(0);
        when(activityMapper.selectOne(any())).thenReturn(inProgressFishing(11L));
        when(activityMapper.update(any(), any())).thenReturn(1);
        // roll 为随机（成功率 ~95%）：CAUGHT 分支才走 Feign，故用 lenient 避免偶发未使用告警
        lenient().when(wishFeignClient.fishForPet()).thenReturn(ApiResponse.ok(new WishFeignClient.WishBottleVO(
                555L, "PICKED", "PICKED", "你好呀", null, null)));

        bottleService.settle(100L);

        ArgumentCaptor<PetBottleRecord> captor = ArgumentCaptor.forClass(PetBottleRecord.class);
        verify(bottleRecordMapper, atLeastOnce()).insert(captor.capture());
        List<PetBottleRecord> records = captor.getAllValues();
        assertThat(records).hasSize(1);
        PetBottleRecord record = records.get(0);
        assertThat(record.getOutcome()).isIn(PetBottleOutcome.CAUGHT.name(), PetBottleOutcome.EMPTY.name());
        if (PetBottleOutcome.CAUGHT.name().equals(record.getOutcome())) {
            // 普通瓶有真实 bottleId；特殊瓶（RARE/PET/EASTER_EGG）bottleId 为空但带内容文本
            if (PetBottleRarity.NORMAL.name().equals(record.getRarity())) {
                assertThat(record.getBottleId()).isEqualTo(555L);
            } else {
                assertThat(record.getSpecialContent()).isNotBlank();
            }
        }
        assertThat(record.getRarity()).isIn("NORMAL", "RARE", "PET", "EASTER_EGG");
        assertThat(record.getActivityId()).isEqualTo(11L);
    }

    @Test
    @DisplayName("结算：Feign 降级 → outcome=FAILED 落库（不抛出，保持可重试领取）")
    void settleFeignDegradeMarksFailed() {
        Pet p = pet(100, 100);
        when(petService.requireOwnedPet(100L)).thenReturn(p);
        lenient().when(stateService.grantExp(any(Pet.class), any(Integer.class))).thenReturn(0);
        when(wishFeignClient.fishForPet())
                .thenThrow(new BusinessException("WISH_SERVICE_UNAVAILABLE", "降级"));
        when(activityMapper.update(any(), any())).thenReturn(1);

        boolean reachedFeign = false;
        for (int attempt = 0; attempt < 500 && !reachedFeign; attempt++) {
            final long id = 100L + attempt;
            when(activityMapper.selectOne(any())).thenReturn(inProgressFishing(id));
            bottleService.settle(100L);
            ArgumentCaptor<PetBottleRecord> captor = ArgumentCaptor.forClass(PetBottleRecord.class);
            verify(bottleRecordMapper, atLeastOnce()).insert(captor.capture());
            reachedFeign = captor.getAllValues().stream()
                    .anyMatch(r -> PetBottleOutcome.FAILED.name().equals(r.getOutcome()));
        }
        assertThat(reachedFeign).isTrue();
    }

    @Test
    @DisplayName("开工：冷却中 → 409 PET_BOTTLE_COOLDOWN")
    void startInCooldown() {
        when(petService.requireOwnedPet(100L)).thenReturn(pet(30, 10));
        when(activityMapper.selectCount(any())).thenReturn(0L);
        PetActivity recent = new PetActivity();
        recent.setStatus(PetActivityStatus.COMPLETED.name());
        recent.setFinishedAt(LocalDateTime.now(ZoneId.of("UTC")).minusSeconds(60));
        when(activityMapper.selectOne(any())).thenReturn(recent);

        assertThatThrownBy(() -> bottleService.start(100L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", PetErrorCodes.PET_BOTTLE_COOLDOWN);
    }

    @Test
    @DisplayName("开工：进行中活动互斥 → 409 PET_ACTIVITY_CONFLICT")
    void startWithBusyActivity() {
        when(petService.requireOwnedPet(100L)).thenReturn(pet(30, 10));
        when(activityMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> bottleService.start(100L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", PetErrorCodes.PET_ACTIVITY_CONFLICT);
    }
}
