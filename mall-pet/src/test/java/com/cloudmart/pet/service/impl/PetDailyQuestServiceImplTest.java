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

    /** LambdaWrapper 需要实体元数据（与既有测试同一口径：不启动 Spring 也能构造条件） */
    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PetDailyQuest.class);
    }

    @BeforeEach
    void setUp() {
        economyService = org.mockito.Mockito.mock(PetEconomyService.class);
                org.mockito.Mockito.when(economyService.earn(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(Object[].class)))
                .thenReturn(new PetOperationService.WalletSettlement("COMPLETED", 0L, 1000L, false, null));
        questService = new PetDailyQuestServiceImpl(petService, stateService, configMapper, questMapper,
                wishFeignClient, economyService, org.mockito.Mockito.mock(com.cloudmart.pet.config.PetClock.class), intimacyService, achievementService, properties,
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
                });
        lenient().when(petService.requireOwnedPet(100L)).thenReturn(pet());
        lenient().when(configMapper.selectList(any())).thenReturn(List.of(config()));
        lenient().when(configMapper.selectOne(any())).thenReturn(config());
        lenient().when(stateService.grantExp(any(), anyInt())).thenReturn(0);
        lenient().when(intimacyService.gain(any(), any())).thenReturn(0);
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
}
