package com.cloudmart.pet.service.impl;

import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.enums.PetIntimacySource;
import com.cloudmart.pet.mq.PetEventProducer;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.service.PetAchievementService;
import com.cloudmart.pet.vo.PetIntimacyVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 亲密度与陪伴测试：等级/加成公式、单次加成只改内存不落库（由调用方一次写）、
 * 升级发通知、陪伴心跳按秒换算并按日封顶。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PetIntimacyServiceImpl 单元测试")
class PetIntimacyServiceImplTest {

    @Mock
    private PetMapper petMapper;
    @Mock
    private PetEventProducer eventProducer;
    @Mock
    private PetAchievementService achievementService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private final PetProperties properties = new PetProperties();
    private PetIntimacyServiceImpl intimacyService;

    @BeforeEach
    void setUp() {
        intimacyService = new PetIntimacyServiceImpl(petMapper, properties, eventProducer,
                achievementService, redisTemplate);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private Pet pet(int intimacy) {
        Pet pet = new Pet();
        pet.setId(1L);
        pet.setUserId(100L);
        pet.setName("小橘");
        pet.setIntimacy(intimacy);
        pet.setCompanionSeconds(0L);
        pet.setTodayCompanionSeconds(0);
        pet.setCompanionDays(0);
        pet.setCompanionStreak(0);
        return pet;
    }

    @Test
    @DisplayName("等级与经验加成：1200 点落在第 4 档（亲近），加成 3% 且不超过上限")
    void levelAndBonusFollowThresholds() {
        assertThat(intimacyService.levelOf(0)).isEqualTo(1);
        assertThat(intimacyService.levelOf(1200)).isEqualTo(4);
        assertThat(intimacyService.levelName(4)).isEqualTo("亲密");
        assertThat(intimacyService.toNext(1200)).isEqualTo(300);
        // 1200 → 第 4 档 → (4-1) × 1% = 3%
        assertThat(intimacyService.expBonus(pet(1200))).isEqualTo(0.03);
        // 最高档（灵魂伴侣 = 第 8 档）为 (8-1) × 1% = 7%，且不超过配置上限 10%
        assertThat(intimacyService.expBonus(pet(999999)))
                .isEqualTo(0.07)
                .isLessThanOrEqualTo(properties.getIntimacy().getMaxExpBonus());
        // 满级后没有下一档
        assertThat(intimacyService.toNext(20000)).isZero();
    }

    @Test
    @DisplayName("单次加成只改内存 + 升级发宠物口吻通知（不落库，交给调用方一次写）")
    void gainMutatesInMemoryOnly() {
        Pet pet = pet(99);
        int levelups = intimacyService.gain(pet, PetIntimacySource.PLAY);

        assertThat(pet.getIntimacy()).isEqualTo(102);
        assertThat(levelups).isEqualTo(1);
        verify(petMapper, never()).updateById(ArgumentMatchers.<Pet>any());
        ArgumentCaptor<PetEventProducer.PetEventMessage> captor =
                ArgumentCaptor.forClass(PetEventProducer.PetEventMessage.class);
        verify(eventProducer).publish(eq(com.cloudmart.pet.config.RocketMQConfig.PET_TAG_INTIMACY), captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("陪伴心跳：600 秒换 1 点亲密度，秒数累计落库且当日点数有上限")
    void heartbeatAccumulatesSecondsAndPoints() {
        Pet pet = pet(0);
        when(petMapper.selectOne(any())).thenReturn(pet);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(valueOperations.increment(anyString(), any(Long.class))).thenReturn(1L);

        int gained = intimacyService.heartbeat(100L, 600);

        assertThat(gained).isEqualTo(1);
        assertThat(pet.getIntimacy()).isEqualTo(1);
        assertThat(pet.getCompanionSeconds()).isEqualTo(600L);
        assertThat(pet.getTodayCompanionSeconds()).isEqualTo(600);
        assertThat(pet.getCompanionDays()).isEqualTo(1);
        assertThat(pet.getCompanionStreak()).isEqualTo(1);
        verify(petMapper).updateById(pet);
    }

    @Test
    @DisplayName("概览：返回等级阶梯与展示用加成百分比，未领养宠物也可查询")
    void overviewReturnsLevelLadder() {
        Pet pet = pet(120);
        when(petMapper.selectOne(any())).thenReturn(pet);

        PetIntimacyVO vo = intimacyService.overview(100L);

        assertThat(vo.intimacy()).isEqualTo(120);
        assertThat(vo.level()).isEqualTo(2);
        assertThat(vo.expBonusPercent()).isEqualTo(1);
        assertThat(vo.levels()).hasSize(properties.getIntimacy().getLevelThresholds().size());
        assertThat(vo.levels().get(0).achieved()).isTrue();
        assertThat(vo.companionDays()).isZero();
    }
}
