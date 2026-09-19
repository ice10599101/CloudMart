package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.repository.PetMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 状态懒更新 + 成长曲线单元测试（数值权威在服务端的核心保障）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PetStateService 单元测试")
class PetStateServiceTest {

    @Mock
    private PetMapper petMapper;

    private PetStateService stateService;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Pet.class);
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        stateService = new PetStateService(petMapper, new PetProperties());
    }

    private Pet pet(int hunger, int happiness, int energy, int cleanliness,
                    LocalDateTime lastUpdate) {
        Pet pet = new Pet();
        pet.setId(1L);
        pet.setUserId(100L);
        pet.setLevel(1);
        pet.setExp(0);
        pet.setHp(100);
        pet.setMaxHp(100);
        pet.setHunger(hunger);
        pet.setHappiness(happiness);
        pet.setEnergy(energy);
        pet.setCleanliness(cleanliness);
        pet.setStrength(5);
        pet.setIntelligence(5);
        pet.setAgility(5);
        pet.setCharm(5);
        pet.setLastStateUpdateAt(lastUpdate);
        return pet;
    }

    @Nested
    @DisplayName("状态懒更新")
    class IdleDecay {

        @Test
        @DisplayName("5 小时：饥饿 -10、心情 -5、精力 +15、清洁 -7.5→-7")
        void decayFiveHours() {
            Pet p = pet(80, 80, 40, 80, LocalDateTime.now(ZoneId.of("UTC")).minusHours(5));
            when(petMapper.update(any(), any())).thenReturn(1);

            stateService.applyIdleDecay(p);

            assertThat(p.getHunger()).isEqualTo(70);
            assertThat(p.getHappiness()).isEqualTo(75);
            assertThat(p.getEnergy()).isEqualTo(55);
            assertThat(p.getCleanliness()).isEqualTo(73);
        }

        @Test
        @DisplayName("0 小时（刚结算过）：数值不变")
        void decayZeroHours() {
            Pet p = pet(80, 80, 40, 80, LocalDateTime.now(ZoneId.of("UTC")));
            Pet result = stateService.applyIdleDecay(p);

            assertThat(result.getHunger()).isEqualTo(80);
            assertThat(result.getEnergy()).isEqualTo(40);
        }

        @Test
        @DisplayName("超过 48h 截断：饥饿不会扣成负数，下限 0")
        void decayClampedToZeroFloor() {
            Pet p = pet(3, 3, 3, 3, LocalDateTime.now(ZoneId.of("UTC")).minusHours(72));
            when(petMapper.update(any(), any())).thenReturn(1);

            stateService.applyIdleDecay(p);

            assertThat(p.getHunger()).isZero();
            assertThat(p.getHappiness()).isZero();
            assertThat(p.getCleanliness()).isZero();
            assertThat(p.getEnergy()).isEqualTo(100);
        }

        @Test
        @DisplayName("饿肚子（hunger<30）心情额外衰减")
        void hungerAcceleratesMoodDecay() {
            Pet hungryPet = pet(20, 90, 50, 80, LocalDateTime.now(ZoneId.of("UTC")).minusHours(2));
            when(petMapper.update(any(), any())).thenReturn(1);

            stateService.applyIdleDecay(hungryPet);

            // 2h × (1.0 + 1.0) = 4
            assertThat(hungryPet.getHappiness()).isEqualTo(86);
        }

        @Test
        @DisplayName("CAS 未命中（并发写者已结算）：重读最新数据")
        void casMissRereadsLatest() {
            Pet p = pet(80, 80, 40, 80, LocalDateTime.now(ZoneId.of("UTC")).minusHours(5));
            when(petMapper.update(any(), any())).thenReturn(0);
            Pet latest = pet(50, 60, 70, 60, LocalDateTime.now(ZoneId.of("UTC")));
            when(petMapper.selectById(1L)).thenReturn(latest);

            stateService.applyIdleDecay(p);

            assertThat(p.getHunger()).isEqualTo(50);
            assertThat(p.getEnergy()).isEqualTo(70);
        }
    }

    @Nested
    @DisplayName("经验与升级")
    class ExpAndLevel {

        @Test
        @DisplayName("expToNext(1) = 100×1^1.5 = 100；expToNext(4) = 100×8 = 800")
        void expCurve() {
            assertThat(stateService.expToNext(1)).isEqualTo(100);
            assertThat(stateService.expToNext(4)).isEqualTo(800);
        }

        @Test
        @DisplayName("经验不足：只累计不升级")
        void expAccumulates() {
            Pet p = pet(80, 80, 80, 80, LocalDateTime.now(ZoneId.of("UTC")));
            int levelups = stateService.grantExp(p, 50);

            assertThat(levelups).isZero();
            assertThat(p.getExp()).isEqualTo(50);
            assertThat(p.getLevel()).isEqualTo(1);
        }

        @Test
        @DisplayName("跨多级升级：属性/上限同步成长，成长阶段推进")
        void multiLevelUp() {
            Pet p = pet(80, 80, 80, 80, LocalDateTime.now(ZoneId.of("UTC")));
            p.setLevel(9);
            p.setExp(0);
            int levelups = stateService.grantExp(p, stateService.expToNext(9) + stateService.expToNext(10));

            assertThat(levelups).isEqualTo(2);
            assertThat(p.getLevel()).isEqualTo(11);
            assertThat(p.getExp()).isZero();
            assertThat(p.getMaxHp()).isEqualTo(110);
            assertThat(p.getGrowthStage()).isEqualTo("YOUNG");
            assertThat(p.getStrength()).isEqualTo(7);
        }
    }

    @Test
    @DisplayName("成长阶段划分：1-9 幼年 / 10-19 成长 / 20+ 成年")
    void growthStageBoundaries() {
        assertThat(stateService.growthStageFor(1)).isEqualTo("BABY");
        assertThat(stateService.growthStageFor(9)).isEqualTo("BABY");
        assertThat(stateService.growthStageFor(10)).isEqualTo("YOUNG");
        assertThat(stateService.growthStageFor(19)).isEqualTo("YOUNG");
        assertThat(stateService.growthStageFor(20)).isEqualTo("ADULT");
    }

    @Test
    @DisplayName("CAS 命中时游标推进为 now（UTC）")
    void cursorAdvances() {
        LocalDateTime before = LocalDateTime.now(ZoneId.of("UTC")).minusHours(1);
        Pet p = pet(80, 80, 80, 80, before);
        when(petMapper.update(any(), any())).thenReturn(1);

        stateService.applyIdleDecay(p);

        assertThat(p.getLastStateUpdateAt()).isCloseTo(LocalDateTime.now(ZoneId.of("UTC")), within(java.time.Duration.ofSeconds(5)));
        ArgumentCaptor<Pet> captor = ArgumentCaptor.forClass(Pet.class);
        org.mockito.Mockito.verify(petMapper).update(eq(null), any());
        assertThat(captor).isNotNull();
    }
}
