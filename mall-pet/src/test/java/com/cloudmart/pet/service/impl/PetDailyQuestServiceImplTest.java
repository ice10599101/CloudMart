package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
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
    private PetOperationService operationService;
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
        operationService = org.mockito.Mockito.mock(PetOperationService.class);
        org.mockito.Mockito.when(operationService.executeEarn(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PetOperationService.WalletSettlement("COMPLETED", 0, 1000, false, null));
        org.mockito.Mockito.lenient().when(operationService.operationKey(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Object[].class))).thenReturn("OP:TEST");
        questService = new PetDailyQuestServiceImpl(petService, stateService, configMapper, questMapper,
                wishFeignClient, operationService, org.mockito.Mockito.mock(com.cloudmart.pet.config.PetClock.class), intimacyService, achievementService, properties);
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
        verify(operationService).executeEarn(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(100L), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("QUEST_CLAIM"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(20), org.mockito.ArgumentMatchers.any());
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
        verify(operationService).executeEarn(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(100L), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("QUEST_CHEST"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(properties.getDailyQuest().getChestCurrency()), org.mockito.ArgumentMatchers.any());
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
