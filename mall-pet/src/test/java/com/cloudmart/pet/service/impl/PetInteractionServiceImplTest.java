package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.config.RocketMQConfig;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetActivity;
import com.cloudmart.pet.mq.PetEventProducer;
import com.cloudmart.pet.repository.PetActivityMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.service.PetAchievementService;
import com.cloudmart.pet.service.PetDailyQuestService;
import com.cloudmart.pet.enums.PetActivityStatus;
import com.cloudmart.pet.enums.PetActivityType;
import com.cloudmart.pet.enums.PetStatus;
import com.cloudmart.pet.service.PetHomeService;
import com.cloudmart.pet.service.PetIntimacyService;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.vo.PetVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 基础互动测试：喂食限频走配置（非硬编码）、升级统一发 MQ 事件、休息留痕后评估成就。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PetInteractionServiceImpl 单元测试")
class PetInteractionServiceImplTest {

    @Mock
    private PetService petService;
    @Mock
    private PetStateService stateService;
    @Mock
    private PetActivityMapper activityMapper;
    @Mock
    private PetMapper petMapper;
    @Mock
    private PetQuotaService quotaService;
    private PetOutboxService outboxService;
    @Mock
    private PetAchievementService achievementService;
    @Mock
    private PetDailyQuestService dailyQuestService;
    @Mock
    private PetIntimacyService intimacyService;
    @Mock
    private PetHomeService homeService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private PetEventProducer eventProducer;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private final PetProperties properties = new PetProperties();
    private PetInteractionServiceImpl interactionService;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PetActivity.class);
    }

    @BeforeEach
    void setUp() {
        com.cloudmart.pet.config.PetClock petClock = org.mockito.Mockito.mock(com.cloudmart.pet.config.PetClock.class);
        org.mockito.Mockito.when(petClock.nowUtc())
                .thenAnswer(inv -> java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
        quotaService = org.mockito.Mockito.mock(PetQuotaService.class);
        org.mockito.Mockito.when(quotaService.tryConsume(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);
        org.mockito.Mockito.lenient().when(petMapper.update(any(), any())).thenReturn(1);
        org.mockito.Mockito.lenient().when(petMapper.updateById(org.mockito.ArgumentMatchers.any(com.cloudmart.pet.entity.Pet.class))).thenReturn(1);
        outboxService = org.mockito.Mockito.mock(PetOutboxService.class);
        interactionService = new PetInteractionServiceImpl(petService, stateService, activityMapper,
                petMapper, achievementService, dailyQuestService, intimacyService, homeService,
                properties, quotaService, outboxService, petClock,
                org.mockito.Mockito.mock(PetCompanionFeatureService.class), org.mockito.Mockito.mock(PetPlayFeatureService.class));
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(petService.getMyPet(any())).thenReturn(petVo());
    }

    private Pet pet() {
        Pet pet = new Pet();
        pet.setId(1L);
        pet.setUserId(100L);
        pet.setName("小橘");
        pet.setLevel(1);
        pet.setHunger(50);
        pet.setHappiness(50);
        pet.setEnergy(80);
        pet.setCleanliness(50);
        pet.setHp(80);
        pet.setMaxHp(100);
        return pet;
    }

    private PetVO petVo() {
        return new PetVO(1L, 100L, "小橘", "CAT", "MALE", "{}", "LIVELY", 2, 0, 200, "BABY",
                80, 100, 60, 60, 100, 60, 5, 5, 5, 5, "IDLE", null, null, null,
                true, null, LocalDateTime.now(ZoneId.of("UTC")),
                0, null, 1, 3,
                0, 1, "初识", 100, 0, 0L, 0, 0, 0, null, null, null);
    }

    @Test
    @DisplayName("喂食：超过配置上限抛 429，且以配置值为准")
    void feedRespectsConfiguredDailyLimit() {
        Pet pet = pet();
        when(petService.requireOwnedPet(100L)).thenReturn(pet);
        properties.getInteraction().setFeedDailyLimit(2);
        // B06：喂食额度数据库权威（tryConsume 返回 false = 今日已用尽）
        when(quotaService.tryConsume(any(), eq(PetQuotaService.QuotaType.FEED), eq(0L), eq(2))).thenReturn(false);

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> interactionService.feed(100L))
                .isInstanceOf(com.cloudmart.common.exception.BusinessException.class)
                .extracting(e -> ((com.cloudmart.common.exception.BusinessException) e).getCode())
                .isEqualTo("PET_INTERACTION_RATE_LIMITED");
    }

    @Test
    @DisplayName("喂食升级：与其他场景一致发送 level-up 事件")
    void feedLevelUpPublishesEvent() {
        Pet pet = pet();
        when(petService.requireOwnedPet(100L)).thenReturn(pet);
        when(quotaService.tryConsume(any(), eq(PetQuotaService.QuotaType.FEED), eq(0L), eq(5))).thenReturn(true);
        when(stateService.grantExp(any(Pet.class), eq(properties.getInteraction().getFeedExp()))).thenReturn(1);

        interactionService.feed(100L);

        verify(achievementService).evaluate(eq(pet), eq(PetAchievementService.Event.LEVEL_UP));
        // B19：升级事件经 outbox 可靠投递
        verify(outboxService).record(org.mockito.ArgumentMatchers.anyString(),
                eq(com.cloudmart.pet.config.RocketMQConfig.PET_TAG_LEVEL_UP), eq(100L), eq(1L), any());
    }

    @Test
    @DisplayName("未升级时不发送 level-up 事件（避免噪音通知）")
    void noLevelUpNoEvent() {
        Pet pet = pet();
        when(petService.requireOwnedPet(100L)).thenReturn(pet);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(stateService.grantExp(any(Pet.class), any(Integer.class))).thenReturn(0);

        interactionService.feed(100L);

        verify(eventProducer, never()).publish(eq(RocketMQConfig.PET_TAG_LEVEL_UP),
                any(PetEventProducer.PetEventMessage.class));
    }

    @Test
    @DisplayName("休息：留痕后评估成就（此前漏评估）")
    void restEvaluatesAchievement() {
        // B06：休息改为定时活动——开始时不发成就；到期结算（settleRest）恢复状态并给配额内亲密度
        Pet pet = pet();
        pet.setEnergy(40);
        when(petService.requireOwnedPet(100L)).thenReturn(pet);
        when(activityMapper.selectCount(any())).thenReturn(0L);
        when(petMapper.update(any(), any())).thenReturn(1);

        interactionService.rest(100L);

        verify(activityMapper).insert(any(PetActivity.class));
        org.assertj.core.api.Assertions.assertThat(pet.getStatus()).isEqualTo(PetStatus.RESTING.name());
    }

    @Test
    @DisplayName("休息到期结算：恢复精力/生命并评估成就（额度内）")
    void settleRestAppliesEffects() {
        Pet pet = pet();
        pet.setEnergy(40);
        when(petService.requireOwnedPet(100L)).thenReturn(pet);
        PetActivity rest = new PetActivity();
        rest.setId(30L);
        rest.setUserId(100L);
        rest.setPetId(1L);
        rest.setActivityType(PetActivityType.REST.name());
        rest.setStatus(PetActivityStatus.IN_PROGRESS.name());
        rest.setFinishedAt(LocalDateTime.now(ZoneId.of("UTC")).minusSeconds(1));
        when(activityMapper.selectOne(any())).thenReturn(rest);
        when(activityMapper.update(any(), any())).thenReturn(1);
        when(quotaService.tryConsume(any(), eq(PetQuotaService.QuotaType.REST_INTIMACY), eq(0L), eq(3))).thenReturn(true);

        interactionService.settleRest(100L);

        verify(achievementService).evaluate(eq(pet), eq(PetAchievementService.Event.REST));
        org.assertj.core.api.Assertions.assertThat(pet.getEnergy()).isEqualTo(100);
    }
}
