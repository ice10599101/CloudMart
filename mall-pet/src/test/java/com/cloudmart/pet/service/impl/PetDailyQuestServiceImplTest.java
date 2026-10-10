package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.wallet.PetEconomyService;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetDailyQuest;
import com.cloudmart.pet.entity.PetDailyQuestConfig;
import com.cloudmart.pet.feign.WishFeignClient;
import com.cloudmart.pet.repository.PetDailyQuestConfigMapper;
import com.cloudmart.pet.repository.PetDailyQuestMapper;
import com.cloudmart.pet.service.PetAchievementService;
import com.cloudmart.pet.service.PetIntimacyService;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.vo.PetDailyQuestItemVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 每日任务测试：领奖 CAS 幂等契约（未完成 409 / 已完成发放经验+星光）、
 * 全清宝箱条件（有未领任务时 409）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PetDailyQuestServiceImpl 单元测试")
class PetDailyQuestServiceImplTest {

    private static final String QUEST_CODE = "daily_feed";

    @Mock
    private PetService petService;
    @Mock
    private PetStateService stateService;
    @Mock
    private PetDailyQuestConfigMapper configMapper;
    @Mock
    private PetDailyQuestMapper questMapper;
    @Mock
    private PetEconomyService economyService;
    @Mock
    private WishFeignClient wishFeignClient;
    @Mock
    private PetIntimacyService intimacyService;
    @Mock
    private PetAchievementService achievementService;

    private final PetProperties properties = new PetProperties();
    private PetDailyQuestServiceImpl questService;
    private com.cloudmart.pet.config.PetClock petClock;
    private com.cloudmart.pet.repository.PetQuestEventReceiptMapper receiptMapper;
    private com.cloudmart.pet.repository.PetMapper petMapperMock;
    private com.cloudmart.pet.repository.PetDailyQuestSetMapper setMapper;

    /** LambdaWrapper 需要实体元数据（与既有测试同一口径：不启动 Spring 也能构造条件） */
    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PetDailyQuest.class);
        TableInfoHelper.initTableInfo(assistant, com.cloudmart.pet.entity.PetQuestEventReceipt.class);
        TableInfoHelper.initTableInfo(assistant, com.cloudmart.pet.entity.PetDailyQuestSet.class);
    }

    @BeforeEach
    void setUp() {
        economyService = org.mockito.Mockito.mock(PetEconomyService.class);
                org.mockito.Mockito.when(economyService.earn(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(Object[].class)))
                .thenReturn(new PetEconomyService.WalletSettlement("COMPLETED", 0L, 1000L, false, null));
        petClock = org.mockito.Mockito.mock(com.cloudmart.pet.config.PetClock.class);
        receiptMapper = org.mockito.Mockito.mock(com.cloudmart.pet.repository.PetQuestEventReceiptMapper.class);
        petMapperMock = org.mockito.Mockito.mock(com.cloudmart.pet.repository.PetMapper.class);
        setMapper = org.mockito.Mockito.mock(com.cloudmart.pet.repository.PetDailyQuestSetMapper.class);
        // PET-09：ensureQuestSet 需要真实业务日/截止换算——PetClock 为 mock，补最小桩
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
        org.mockito.Mockito.lenient().when(petClock.businessDate()).thenReturn(today);
        org.mockito.Mockito.lenient().when(petClock.businessDateOf(org.mockito.ArgumentMatchers.any())).thenReturn(today);
        org.mockito.Mockito.lenient().when(petClock.businessDateStartUtc(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
        org.mockito.Mockito.lenient().when(petClock.nowUtc()).thenReturn(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
        // 默认返回既有任务集（ensureQuestSet 走读路径）
        com.cloudmart.pet.entity.PetDailyQuestSet questSet = new com.cloudmart.pet.entity.PetDailyQuestSet();
        questSet.setId(99L);
        questSet.setUserId(100L);
        questSet.setPetId(1L);
        questSet.setBusinessDate(today);
        questSet.setLevelSnapshot(5);
        questSet.setClaimDeadline(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).plusDays(1));
        org.mockito.Mockito.lenient().when(setMapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(questSet);
        org.mockito.Mockito.lenient().when(setMapper.selectById(org.mockito.ArgumentMatchers.any())).thenReturn(questSet);
        org.mockito.Mockito.lenient().when(setMapper.insert(org.mockito.ArgumentMatchers.any(com.cloudmart.pet.entity.PetDailyQuestSet.class))).thenReturn(1);
        questService = new PetDailyQuestServiceImpl(petService, stateService, configMapper, questMapper,
                receiptMapper,
                petMapperMock,
                wishFeignClient, economyService, petClock, intimacyService, achievementService, properties,
                // R13：自代理提供者——测试中直通返回本实例（事务由生产代理承担，单测验证编排语义）
                new org.springframework.beans.factory.ObjectProvider<com.cloudmart.pet.service.PetDailyQuestService>() {
                    @Override
                    public com.cloudmart.pet.service.PetDailyQuestService getObject(Object... args) {
                        return questService;
                    }

                    @Override
                    public com.cloudmart.pet.service.PetDailyQuestService getIfAvailable() {
                        return questService;
                    }

                    @Override
                    public com.cloudmart.pet.service.PetDailyQuestService getIfUnique() {
                        return questService;
                    }

                    @Override
                    public java.util.stream.Stream<com.cloudmart.pet.service.PetDailyQuestService> stream() {
                        return java.util.stream.Stream.of(questService);
                    }
                },
                setMapper,
                org.mockito.Mockito.mock(com.cloudmart.pet.config.PetMetrics.class),
                org.mockito.Mockito.mock(com.cloudmart.pet.service.PetSeasonPassService.class));
        lenient().when(petService.requireOwnedPet(100L)).thenReturn(pet());
        // PET-09：按集领取解析集绑定宠物；进度投影按当日任务行（codesOfType 走行快照）
        lenient().when(petMapperMock.selectById(org.mockito.ArgumentMatchers.eq(1L))).thenReturn(pet());
        lenient().when(configMapper.selectList(any())).thenReturn(List.of(config()));
        lenient().when(configMapper.selectOne(any())).thenReturn(config());
        lenient().when(stateService.grantExp(any(), anyInt())).thenReturn(0);
        lenient().when(intimacyService.gain(any(), any())).thenReturn(0);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("PET-09/T14：列表展示读任务行快照（模板改名/改奖励不改变已生成任务展示）")
    void listShowsSnapshotDisplayNotCurrentConfig() {
        PetDailyQuest row = quest("COMPLETE");
        row.setSetId(99L);
        row.setRewardSnapshot(com.cloudmart.pet.util.PetJsonUtils.toJson(java.util.Map.of(
                "name", "快照名", "description", "快照描述", "icon", "⭐", "questType", "FEED",
                "expReward", 77, "currencyReward", 88, "actionTarget", "FEED")));
        PetDailyQuest chestRow = chest("IN_PROGRESS");
        chestRow.setSetId(99L);
        lenient().when(questMapper.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new java.util.ArrayList<>(java.util.List.of(row, chestRow)));

        com.cloudmart.pet.vo.PetDailyQuestVO vo = questService.list(100L);

        assertThat(vo.quests().get(0).name()).isEqualTo("快照名");
        assertThat(vo.quests().get(0).expReward()).isEqualTo(77);
        assertThat(vo.quests().get(0).currencyReward()).isEqualTo(88);
        assertThat(vo.quests().get(0).actionTarget()).isEqualTo("FEED");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("PET-09/T15：领取截止已过（24h 宽限结束）→ 拒绝领取")
    void claimInSetRejectedAfterDeadline() {
        com.cloudmart.pet.entity.PetDailyQuestSet expiredSet = set99();
        expiredSet.setClaimDeadline(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusHours(1));
        lenient().when(setMapper.selectById(99L)).thenReturn(expiredSet);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> questService.claimInSet(100L, 99L, "11"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", com.cloudmart.pet.constant.PetErrorCodes.PET_QUEST_NOT_FINISHED);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("PET-09/T17：按集领取绑定集的原宠物（切宠后不错对象）")
    void claimInSetBindsOriginalPet() {
        com.cloudmart.pet.entity.PetDailyQuestSet set = set99();
        set.setPetId(2L);
        lenient().when(setMapper.selectById(99L)).thenReturn(set);
        Pet originalPet = pet();
        originalPet.setId(2L);
        lenient().when(petMapperMock.selectById(2L)).thenReturn(originalPet);
        PetDailyQuest row = quest("COMPLETE");
        row.setSetId(99L);
        row.setId(11L);
        row.setRewardSnapshot(com.cloudmart.pet.util.PetJsonUtils.toJson(java.util.Map.of(
                "name", "好好吃饭", "questType", "FEED", "expReward", 20, "currencyReward", 20)));
        lenient().when(questMapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(row);
        lenient().when(questMapper.update(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(1);
        lenient().when(questMapper.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new java.util.ArrayList<>(java.util.List.of(row)));

        PetDailyQuestItemVO vo = questService.claimInSet(100L, 99L, "11");

        assertThat(vo.code()).isEqualTo(QUEST_CODE);
        // 经验发给集绑定的宠物 2，而非当前主宠 1
        org.mockito.Mockito.verify(stateService).grantExp(
                org.mockito.ArgumentMatchers.argThat(pp -> pp != null && Long.valueOf(2L).equals(pp.getId())),
                org.mockito.ArgumentMatchers.eq(20));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("PET-09/T07：非本人任务集领取 → PET_QUEST_NOT_FOUND（归属校验）")
    void claimInSetRejectsForeignSet() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> questService.claimInSet(999L, 99L, "11"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", com.cloudmart.pet.constant.PetErrorCodes.PET_QUEST_NOT_FOUND);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("PET-09/T16：当日升级解锁的高等级配置不追加进既有任务集")
    void levelUpDoesNotExpandFrozenSet() {
        // 集生成时 levelSnapshot=5；新配置要求 10 级（当日新增/升级解锁均不应进当日集）
        PetDailyQuestConfig high = config();
        high.setCode("HIGH_LEVEL");
        high.setRequiredLevel(10);
        lenient().when(configMapper.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(high));
        PetDailyQuest chestRow = chest("IN_PROGRESS");
        chestRow.setSetId(99L);
        lenient().when(questMapper.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new java.util.ArrayList<>(java.util.List.of(chestRow)));

        questService.list(100L);

        // 高等级任务不进当日集（集冻结于 levelSnapshot=5）；不产生任何 HIGH_LEVEL 生成行
        org.mockito.ArgumentCaptor<PetDailyQuest> inserts =
                org.mockito.ArgumentCaptor.forClass(PetDailyQuest.class);
        org.mockito.Mockito.verify(questMapper, org.mockito.Mockito.atLeast(0))
                .insert(inserts.capture());
        assertThat(inserts.getAllValues())
                .extracting(PetDailyQuest::getQuestCode)
                .doesNotContain("HIGH_LEVEL");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("PET-10/T20：进度投影失败 → 回执转 FAILED 排期重试，主动作不感知异常")
    void projectionFailureMarksReceiptFailed() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
        org.mockito.Mockito.lenient().when(petClock.businessDate()).thenReturn(java.time.LocalDate.now());
        org.mockito.Mockito.lenient().when(petClock.businessDateOf(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.time.LocalDate.now());
        org.mockito.Mockito.lenient().when(petClock.nowUtc())
                .thenReturn(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
        when(receiptMapper.insert(org.mockito.ArgumentMatchers.any(com.cloudmart.pet.entity.PetQuestEventReceipt.class)))
                .thenReturn(1);
        // 投影期间进度累加失败（如数据库瞬时故障）
        when(questMapper.update(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("DB down"));
        lenient().when(questMapper.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new java.util.ArrayList<>(java.util.List.of(quest("IN_PROGRESS"))));

        boolean applied = questService.recordFact(pet(), com.cloudmart.pet.enums.PetQuestType.FEED, "F:fail", now, 1);

        assertThat(applied).isFalse();
        // 回执落 FAILED + 指数退避重试时间 + attempts=1（与进度失败同事务，此处验证编排）
        org.mockito.ArgumentCaptor<com.cloudmart.pet.entity.PetQuestEventReceipt> saved =
                org.mockito.ArgumentCaptor.forClass(com.cloudmart.pet.entity.PetQuestEventReceipt.class);
        org.mockito.Mockito.verify(receiptMapper).updateById(saved.capture());
        org.assertj.core.api.Assertions.assertThat(saved.getValue().getStatus()).isEqualTo("FAILED");
        org.assertj.core.api.Assertions.assertThat(saved.getValue().getAttempts()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(saved.getValue().getNextRetryAt()).isNotNull();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("PET-10/T21：到期 FAILED 回执重放恰好计一次进度")
    void retryFailedReceiptsAppliesDueReceipt() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
        org.mockito.Mockito.lenient().when(petClock.nowUtc()).thenReturn(now);
        com.cloudmart.pet.entity.PetQuestEventReceipt receipt = new com.cloudmart.pet.entity.PetQuestEventReceipt();
        receipt.setId(900L);
        receipt.setPetId(1L);
        receipt.setUserId(100L);
        receipt.setQuestCode("FEED");
        receipt.setEventId("F:due");
        receipt.setAmount(1);
        receipt.setBusinessDate(java.time.LocalDate.now());
        receipt.setStatus("FAILED");
        receipt.setAttempts(1);
        receipt.setNextRetryAt(now.minusSeconds(1));
        when(receiptMapper.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.List.of(receipt));
        // CAS FAILED→APPLIED 单胜 + 进度累加命中
        when(receiptMapper.update(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(1);
        when(questMapper.update(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(1);
        lenient().when(questMapper.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new java.util.ArrayList<>(java.util.List.of(quest("IN_PROGRESS"))));

        int applied = questService.retryFailedReceipts();

        assertThat(applied).isEqualTo(1);
        // 进度累加恰好一次（CAS 胜者才投影，重放不翻倍）
        org.mockito.Mockito.verify(questMapper, org.mockito.Mockito.times(1))
                .update(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("PET-10：无到期 FAILED 回执 → 空转返回 0")
    void retryFailedReceiptsNoopWithoutDue() {
        org.mockito.Mockito.lenient().when(petClock.nowUtc())
                .thenReturn(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
        when(receiptMapper.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.List.of());

        assertThat(questService.retryFailedReceipts()).isZero();
    }

    /** 默认任务集（setUp 桩同一实例语义） */
    private com.cloudmart.pet.entity.PetDailyQuestSet set99() {
        com.cloudmart.pet.entity.PetDailyQuestSet set = new com.cloudmart.pet.entity.PetDailyQuestSet();
        set.setId(99L);
        set.setUserId(100L);
        set.setPetId(1L);
        set.setBusinessDate(java.time.LocalDate.now(java.time.ZoneOffset.UTC));
        set.setLevelSnapshot(5);
        set.setClaimDeadline(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).plusDays(1));
        return set;
    }

    private Pet pet() {
        Pet pet = new Pet();
        pet.setId(1L);
        pet.setUserId(100L);
        pet.setName("小橘");
        pet.setLevel(5);
        pet.setIntimacy(0);
        return pet;
    }

    private PetDailyQuestConfig config() {
        PetDailyQuestConfig config = new PetDailyQuestConfig();
        config.setCode(QUEST_CODE);
        config.setName("好好吃饭");
        config.setDescription("喂食 2 次");
        config.setIcon("🍖");
        config.setQuestType("FEED");
        config.setTargetValue(2);
        config.setExpReward(20);
        config.setCurrencyReward(20);
        config.setRequiredLevel(1);
        config.setEnabled(true);
        return config;
    }

    private PetDailyQuest quest(String status) {
        PetDailyQuest quest = new PetDailyQuest();
        quest.setId(11L);
        quest.setPetId(1L);
        quest.setUserId(100L);
        quest.setQuestDate(LocalDate.now(ZoneId.of("UTC")));
        quest.setQuestCode(QUEST_CODE);
        quest.setProgress(2);
        quest.setTargetValue(2);
        quest.setStatus(status);
        return quest;
    }

    private PetDailyQuest chest(String status) {
        PetDailyQuest chest = new PetDailyQuest();
        chest.setId(12L);
        chest.setPetId(1L);
        chest.setUserId(100L);
        chest.setQuestDate(LocalDate.now(ZoneId.of("UTC")));
        chest.setQuestCode(PetDailyQuestServiceImpl.CHEST_CODE);
        chest.setProgress(0);
        chest.setTargetValue(1);
        chest.setStatus(status);
        return chest;
    }

    @Test
    @DisplayName("领奖：COMPLETE → CLAIMED，发放经验与星光（CAS 命中）")
    void claimRewardsWhenComplete() {
        PetDailyQuest row = quest("COMPLETE");
        when(questMapper.selectList(any())).thenReturn(List.of(row, chest("IN_PROGRESS")));
        when(questMapper.selectOne(any())).thenReturn(row);
        when(questMapper.update(ArgumentMatchers.<PetDailyQuest>isNull(), any())).thenReturn(1);

        PetDailyQuestItemVO result = questService.claim(100L, QUEST_CODE);

        assertThat(result.status()).isEqualTo("CLAIMED");
        assertThat(result.claimable()).isFalse();
        verify(stateService).grantExp(any(), eq(20));
        org.mockito.Mockito.verify(economyService).earn(org.mockito.ArgumentMatchers.eq(100L), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("QUEST_CLAIM"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(20L), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(Object[].class));
        // 领奖本身也加亲密度（长期陪伴数值）
        verify(intimacyService).gain(any(), any());
    }

    @Test
    @DisplayName("领奖：任务未完成 → 409 PET_QUEST_NOT_FINISHED（不发放任何奖励）")
    void claimRejectedWhenNotFinished() {
        PetDailyQuest row = quest("IN_PROGRESS");
        when(questMapper.selectList(any())).thenReturn(List.of(row, chest("IN_PROGRESS")));
        when(questMapper.selectOne(any())).thenReturn(row);

        assertThatThrownBy(() -> questService.claim(100L, QUEST_CODE))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo("PET_QUEST_NOT_FINISHED");
    }

    @Test
    @DisplayName("宝箱：仍有未领任务 → 409 PET_QUEST_CHEST_NOT_READY")
    void chestRejectedWhenQuestsUnclaimed() {
        when(questMapper.selectList(any())).thenReturn(List.of(quest("COMPLETE"), chest("IN_PROGRESS")));

        assertThatThrownBy(() -> questService.claimChest(100L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo("PET_QUEST_CHEST_NOT_READY");
    }

    @Test
    @DisplayName("宝箱：全部任务已领 → 开箱成功并发经验与星光")
    void chestClaimedWhenAllQuestsClaimed() {
        when(questMapper.selectList(any())).thenReturn(List.of(quest("CLAIMED"), chest("IN_PROGRESS")));
        when(questMapper.update(ArgumentMatchers.<PetDailyQuest>isNull(), any())).thenReturn(1);

        questService.claimChest(100L);

        verify(stateService).grantExp(any(), eq(properties.getDailyQuest().getChestExp()));
        org.mockito.Mockito.verify(economyService).earn(org.mockito.ArgumentMatchers.eq(100L), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("QUEST_CHEST"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq((long) properties.getDailyQuest().getChestCurrency()), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(Object[].class));
    }

    @Test
    @DisplayName("R32 快照冻结：运营把奖励调低后，领取仍按生成时快照发放")
    void claimUsesFrozenSnapshotNotCurrentConfig() {
        PetDailyQuest row = quest("COMPLETE");
        // 生成时快照：exp=20/currency=20；当前配置已被运营改为 exp=5/currency=5
        row.setRewardSnapshot(PetDailyQuestServiceImpl.questSnapshot(config()));
        PetDailyQuestConfig nerfed = config();
        nerfed.setExpReward(5);
        nerfed.setCurrencyReward(5);
        when(questMapper.selectOne(any())).thenReturn(row);
        when(configMapper.selectOne(any())).thenReturn(nerfed);
        when(questMapper.update(any(), any())).thenReturn(1);

        questService.claim(100L, QUEST_CODE);

        // 按快照入账，不按被改低的当前配置
        verify(stateService).grantExp(any(), eq(20));
    }

    @Test
    @DisplayName("R32 宝箱快照：开箱奖励按生成时快照（原实现实时读 properties）")
    void chestUsesFrozenSnapshot() {
        PetDailyQuest row = quest("CLAIMED");
        row.setRewardSnapshot(PetDailyQuestServiceImpl.questSnapshot(config()));
        PetDailyQuest chest = chest("IN_PROGRESS");
        properties.getDailyQuest().setChestExp(60);
        properties.getDailyQuest().setChestCurrency(80);
        // 生成时冻结的宝箱奖励：exp=600/currency=800（与当前配置不同）
        chest.setRewardSnapshot(com.cloudmart.pet.util.PetJsonUtils.toJson(
                java.util.Map.of("chestExp", 600, "chestCurrency", 800)));
        when(questMapper.selectList(any())).thenReturn(List.of(row, chest));
        when(questMapper.update(any(), any())).thenReturn(1);

        questService.claimChest(100L);

        verify(stateService).grantExp(any(), eq(600));
        org.mockito.Mockito.verify(economyService).earn(org.mockito.ArgumentMatchers.eq(100L),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("QUEST_CHEST"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(800L),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(Object[].class));
    }

    @Test
    @DisplayName("R32 宝箱门槛：全部任务被取消 → 宝箱不可白领（空 required 集合拒绝）")
    void chestReadyFalseWhenAllCancelled() {
        assertThat(PetDailyQuestServiceImpl.chestReady(List.of(quest("CANCELLED")))).isFalse();
        assertThat(PetDailyQuestServiceImpl.chestReady(List.of())).isFalse();
        assertThat(PetDailyQuestServiceImpl.chestReady(
                List.of(quest("CANCELLED"), quest("CLAIMED")))).isTrue();
    }

    @Test
    @DisplayName("R32 快照生成：任务快照含名称/类型/奖励/引导动作")
    void questSnapshotContainsContractFields() {
        var snapshot = com.cloudmart.pet.util.PetJsonUtils.parse(
                PetDailyQuestServiceImpl.questSnapshot(config()),
                new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {
                });
        org.assertj.core.api.Assertions.assertThat(snapshot)
                .containsEntry("name", "好好吃饭")
                .containsEntry("questType", "FEED")
                .containsEntry("expReward", 20)
                .containsEntry("currencyReward", 20);
        org.assertj.core.api.Assertions.assertThat(String.valueOf(snapshot.get("actionTarget"))).isNotBlank();
    }

    @Test
    @DisplayName("R13 一键领奖：逐项独立终态——第二项 ALREADY_CLAIMED 不掩盖，宝箱按真实状态评估为 NOT_READY")
    void claimAllPerItemOutcomes() {
        PetDailyQuest first = quest("COMPLETE");
        PetDailyQuest second = quest("COMPLETE");
        // 同一 List 实例被 ensureToday 反复读：claim 的内存状态翻转（CLAIMED）对宝箱评估可见
        java.util.List<PetDailyQuest> rows = new java.util.ArrayList<>(java.util.List.of(first, second, chest("IN_PROGRESS")));
        when(questMapper.selectList(any())).thenReturn(rows);
        // requireQuest 按编码重读：两次迭代命中同一行（first）——
        // 第一次 CAS 成功并翻转内存状态，第二次在状态检查即抛 ALREADY_CLAIMED（真实重复语义）
        when(questMapper.selectOne(any())).thenReturn(first);
        // 序列：first CAS=1 成功、宝箱 mark=1、宝箱 claim=1
        when(questMapper.update(ArgumentMatchers.<PetDailyQuest>isNull(), any()))
                .thenReturn(1, 1, 1);

        com.cloudmart.pet.vo.ClaimAllResult result = questService.claimAll(100L);

        assertThat(result.results()).hasSize(2);
        assertThat(result.results().get(0).status()).isEqualTo("CLAIMED");
        assertThat(result.results().get(0).expReward()).isNotNull();
        // 失败/已领项显式回传，不再伪装成普通任务 VO
        assertThat(result.results().get(1).status()).isEqualTo("ALREADY_CLAIMED");
        assertThat(result.results().get(1).errorCode())
                .isEqualTo(com.cloudmart.pet.constant.PetErrorCodes.PET_QUEST_ALREADY_CLAIMED);
        // 第二项仍 COMPLETE（未领）：宝箱门槛不满足 → NOT_READY，不白领
        assertThat(result.chest().status()).isEqualTo("NOT_READY");
    }

    @Test
    @DisplayName("R13 一键领奖：仅宝箱可领 → results 为空且宝箱真实尝试领取（修复'空列表却提示都收好了'）")
    void claimAllChestOnly() {
        when(questMapper.selectList(any()))
                .thenReturn(java.util.List.of(quest("CLAIMED"), chest("IN_PROGRESS")));
        when(questMapper.update(ArgumentMatchers.<PetDailyQuest>isNull(), any())).thenReturn(1);

        com.cloudmart.pet.vo.ClaimAllResult result = questService.claimAll(100L);

        assertThat(result.results()).isEmpty();
        assertThat(result.chest().status()).isEqualTo("CLAIMED");
        assertThat(result.chest().expReward())
                .isEqualTo(properties.getDailyQuest().getChestExp());
    }

    @Test
    @DisplayName("埋点：口径不匹配时静默跳过（不抛异常、不更新进度）")
    void recordIgnoresUnknownQuestType() {
        when(questMapper.selectList(any())).thenReturn(List.of(quest("IN_PROGRESS"), chest("IN_PROGRESS")));

        questService.record(pet(), com.cloudmart.pet.enums.PetQuestType.CHAT, 1);

        verify(questMapper, org.mockito.Mockito.never())
                .update(ArgumentMatchers.<PetDailyQuest>isNull(), any());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("R32 recordFact：同事实幂等——uk 撞键返回 false 不重复计数")
    void recordFactDedupsSameEventId() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
        org.mockito.Mockito.lenient().when(petClock.businessDate()).thenReturn(java.time.LocalDate.now());
        org.mockito.Mockito.lenient().when(petClock.businessDateOf(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.time.LocalDate.now());
        org.mockito.Mockito.lenient().when(questMapper.update(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(1);
        // 第二次插入撞 uk
        org.mockito.Mockito.when(receiptMapper.insert(org.mockito.ArgumentMatchers
                        .any(com.cloudmart.pet.entity.PetQuestEventReceipt.class)))
                .thenReturn(1)
                .thenThrow(new org.springframework.dao.DuplicateKeyException("uk_quest_event_receipt"));

        org.mockito.Mockito.lenient().when(questMapper.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new java.util.ArrayList<>(java.util.List.of(quest("IN_PROGRESS"))));
        boolean first = questService.recordFact(pet(), com.cloudmart.pet.enums.PetQuestType.FEED, "F:1", now, 1);
        boolean second = questService.recordFact(pet(), com.cloudmart.pet.enums.PetQuestType.FEED, "F:1", now, 1);
        org.assertj.core.api.Assertions.assertThat(first).isTrue();
        org.assertj.core.api.Assertions.assertThat(second).isFalse();
        // 进度累加只发生一次
        org.mockito.Mockito.verify(questMapper, org.mockito.Mockito.times(1))
                .update(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("R32 recordFact：历史事实当日任务行不存在 → SKIPPED_STALE 不涌入今天")
    void recordFactSkipsStaleFact() {
        java.time.LocalDateTime past = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusDays(3);
        java.time.LocalDate today = java.time.LocalDate.now();
        org.mockito.Mockito.when(petClock.businessDate()).thenReturn(today);
        org.mockito.Mockito.when(petClock.businessDateOf(org.mockito.ArgumentMatchers.any()))
                .thenReturn(today.minusDays(3));
        // 历史日无任务行：credit 命中 0
        org.mockito.Mockito.when(questMapper.update(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(0);

        boolean applied = questService.recordFact(pet(), com.cloudmart.pet.enums.PetQuestType.FEED, "F:old", past, 1);
        org.assertj.core.api.Assertions.assertThat(applied).isFalse();
        org.mockito.ArgumentCaptor<com.cloudmart.pet.entity.PetQuestEventReceipt> captor =
                org.mockito.ArgumentCaptor.forClass(com.cloudmart.pet.entity.PetQuestEventReceipt.class);
        org.mockito.Mockito.verify(receiptMapper).updateById(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getStatus()).isEqualTo("SKIPPED_STALE");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("R32 replayReceipt：SKIPPED_STALE 重放成功置 APPLIED")
    void replayReceiptAppliesStaleReceipt() {
        com.cloudmart.pet.entity.PetQuestEventReceipt receipt = new com.cloudmart.pet.entity.PetQuestEventReceipt();
        receipt.setId(900L);
        receipt.setPetId(1L);
        receipt.setUserId(100L);
        receipt.setQuestCode("FEED");
        receipt.setEventId("F:old");
        receipt.setAmount(1);
        receipt.setBusinessDate(java.time.LocalDate.now().minusDays(1));
        receipt.setStatus("SKIPPED_STALE");
        // 模拟 DB：重放后的第二次读返回已置 APPLIED 的行
        org.mockito.Mockito.when(receiptMapper.selectById(900L)).thenReturn(receipt)
                .thenAnswer(inv -> {
                    receipt.setStatus("APPLIED");
                    return receipt;
                });
        org.mockito.Mockito.when(receiptMapper.update(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(1);
        org.mockito.Mockito.when(petMapperMock.selectById(1L)).thenReturn(pet());
        org.mockito.Mockito.when(questMapper.update(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(1);
        // 进度投影按回放日的任务行（PET-09：codesOfType 走行快照）
        org.mockito.Mockito.when(questMapper.selectList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.List.of(quest("IN_PROGRESS")));

        com.cloudmart.pet.entity.PetQuestEventReceipt replayed = questService.replayReceipt(900L);
        org.assertj.core.api.Assertions.assertThat(replayed.getStatus()).isEqualTo("APPLIED");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("R32 replayReceipt：APPLIED 回执拒绝重放")
    void replayReceiptRejectsApplied() {
        com.cloudmart.pet.entity.PetQuestEventReceipt receipt = new com.cloudmart.pet.entity.PetQuestEventReceipt();
        receipt.setId(901L);
        receipt.setQuestCode("FEED");
        receipt.setStatus("APPLIED");
        org.mockito.Mockito.when(receiptMapper.selectById(901L)).thenReturn(receipt);
        // PET-10/T21：APPLIED 重放幂等返回既有结果，不再加一次（原契约抛冲突）
        com.cloudmart.pet.entity.PetQuestEventReceipt replayed = questService.replayReceipt(901L);
        org.assertj.core.api.Assertions.assertThat(replayed.getStatus()).isEqualTo("APPLIED");
        org.mockito.Mockito.verify(questMapper, org.mockito.Mockito.never())
                .update(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
