package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.dto.StartWorkRequest;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetActivity;
import com.cloudmart.pet.entity.PetJobConfig;
import com.cloudmart.pet.enums.PetActivityStatus;
import com.cloudmart.pet.enums.PetActivityType;
import com.cloudmart.pet.feign.WishFeignClient;
import com.cloudmart.pet.repository.PetActivityMapper;
import com.cloudmart.pet.repository.PetJobConfigMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.repository.PetStudyConfigMapper;
import com.cloudmart.pet.mq.PetEventProducer;
import com.cloudmart.pet.service.PetAchievementService;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.vo.PetActivityVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 统一活动（打工/读书）核心契约测试：开工互斥、幂等领取、奖励一致性。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PetActivityServiceImpl 单元测试")
class PetActivityServiceImplTest {

    @Mock
    private PetService petService;
    @Mock
    private PetStateService stateService;
    @Mock
    private PetActivityMapper activityMapper;
    @Mock
    private PetJobConfigMapper jobConfigMapper;
    @Mock
    private PetStudyConfigMapper studyConfigMapper;
    @Mock
    private PetMapper petMapper;
    @Mock
    private WishFeignClient wishFeignClient;
    @Mock
    private PetAchievementService achievementService;
    @Mock
    private PetEventProducer eventProducer;
    @Mock
    private PetStatsService statsService;

    private PetActivityServiceImpl activityService;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Pet.class);
        TableInfoHelper.initTableInfo(assistant, PetActivity.class);
        TableInfoHelper.initTableInfo(assistant, PetJobConfig.class);
    }

    @BeforeEach
    void setUp() {
        activityService = new PetActivityServiceImpl(petService, stateService, activityMapper,
                jobConfigMapper, studyConfigMapper, petMapper, wishFeignClient, achievementService,
                eventProducer, statsService);
        // 技能被动加成（博览群书）默认 0：无技能时与改造前收益口径一致
        lenient().when(statsService.studyExpBonus(any())).thenReturn(0.0);
    }

    private Pet pet() {
        Pet pet = new Pet();
        pet.setId(1L);
        pet.setUserId(100L);
        pet.setName("小橘");
        pet.setLevel(10);
        pet.setExp(0);
        pet.setHp(100);
        pet.setMaxHp(100);
        pet.setHunger(80);
        pet.setHappiness(80);
        pet.setEnergy(100);
        pet.setCleanliness(80);
        pet.setStrength(10);
        pet.setIntelligence(10);
        pet.setAgility(10);
        pet.setCharm(10);
        pet.setGrowthStage("YOUNG");
        pet.setLastStateUpdateAt(LocalDateTime.now(ZoneId.of("UTC")));
        pet.setVersion(0);
        return pet;
    }

    private PetJobConfig job() {
        PetJobConfig job = new PetJobConfig();
        job.setId(9001002L);
        job.setName("咖啡店兼职");
        job.setDurationSeconds(1800);
        job.setEnergyCost(15);
        job.setHungerCost(5);
        job.setExpReward(20);
        job.setCurrencyReward(100);
        job.setRequiredLevel(1);
        job.setEnabled(true);
        return job;
    }

    @Nested
    @DisplayName("开工")
    class StartWork {

        @Test
        @DisplayName("正常开工：扣精力扣饥饿、状态 WORKING、任务 IN_PROGRESS")
        void startSuccessfully() {
            Pet p = pet();
            when(petService.requireOwnedPet(100L)).thenReturn(p);
            when(activityMapper.selectCount(any())).thenReturn(0L);
            when(jobConfigMapper.selectById(9001002L)).thenReturn(job());
            when(activityMapper.insert(any(PetActivity.class))).thenReturn(1);

            PetActivityVO vo = activityService.startWork(100L, new StartWorkRequest(9001002L));

            assertThat(vo.status()).isEqualTo(PetActivityStatus.IN_PROGRESS.name());
            assertThat(vo.remainingSeconds()).isBetween(1795L, 1800L);
            assertThat(p.getEnergy()).isEqualTo(85);
            assertThat(p.getHunger()).isEqualTo(75);
            assertThat(p.getStatus()).isEqualTo("WORKING");
        }

        @Test
        @DisplayName("已有进行中活动：409 PET_ACTIVITY_CONFLICT")
        void busyPetConflicts() {
            when(petService.requireOwnedPet(100L)).thenReturn(pet());
            when(jobConfigMapper.selectById(9001002L)).thenReturn(job());
            when(activityMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> activityService.startWork(100L, new StartWorkRequest(9001002L)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", PetErrorCodes.PET_ACTIVITY_CONFLICT);
        }

        @Test
        @DisplayName("并发开工撞唯一索引：DuplicateKey → 409")
        void duplicateKeyConflicts() {
            Pet p = pet();
            when(petService.requireOwnedPet(100L)).thenReturn(p);
            when(activityMapper.selectCount(any())).thenReturn(0L);
            when(jobConfigMapper.selectById(9001002L)).thenReturn(job());
            when(activityMapper.insert(any(PetActivity.class)))
                    .thenThrow(new org.springframework.dao.DuplicateKeyException("uk_activity_user_active"));

            assertThatThrownBy(() -> activityService.startWork(100L, new StartWorkRequest(9001002L)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", PetErrorCodes.PET_ACTIVITY_CONFLICT);
        }

        @Test
        @DisplayName("等级不足：409 PET_LEVEL_REQUIRED")
        void levelRequired() {
            Pet p = pet();
            p.setLevel(1);
            when(petService.requireOwnedPet(100L)).thenReturn(p);
            when(activityMapper.selectCount(any())).thenReturn(0L);
            PetJobConfig highJob = job();
            highJob.setRequiredLevel(5);
            when(jobConfigMapper.selectById(9001002L)).thenReturn(highJob);

            assertThatThrownBy(() -> activityService.startWork(100L, new StartWorkRequest(9001002L)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", PetErrorCodes.PET_LEVEL_REQUIRED);
        }
    }

    @Nested
    @DisplayName("领取奖励（幂等核心）")
    class ClaimWork {

        @Test
        @DisplayName("未完成就领取：409 PET_ACTIVITY_NOT_FINISHED")
        void notFinished() {
            Pet p = pet();
            lenient().when(petService.requireOwnedPet(100L)).thenReturn(p);
            PetActivity inProgress = new PetActivity();
            inProgress.setId(11L);
            inProgress.setActivityType(PetActivityType.WORK.name());
            inProgress.setStatus(PetActivityStatus.IN_PROGRESS.name());
            inProgress.setConfigId(9001002L);
            inProgress.setFinishedAt(LocalDateTime.now(ZoneId.of("UTC")).plusMinutes(10));
            when(activityMapper.selectOne(any())).thenReturn(inProgress);

            assertThatThrownBy(() -> activityService.claimWork(100L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", PetErrorCodes.PET_ACTIVITY_NOT_FINISHED);
        }

        @Test
        @DisplayName("CAS 抢占失败（重复点击/双端并发）：409 PET_ACTIVITY_ALREADY_CLAIMED")
        void alreadyClaimed() {
            Pet p = pet();
            lenient().when(petService.requireOwnedPet(100L)).thenReturn(p);
            PetActivity completed = new PetActivity();
            completed.setId(11L);
            completed.setActivityType(PetActivityType.WORK.name());
            completed.setStatus(PetActivityStatus.COMPLETED.name());
            completed.setConfigId(9001002L);
            completed.setFinishedAt(LocalDateTime.now(ZoneId.of("UTC")).minusMinutes(5));
            when(activityMapper.selectOne(any())).thenReturn(completed);
            when(activityMapper.update(any(), any())).thenReturn(0);

            assertThatThrownBy(() -> activityService.claimWork(100L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", PetErrorCodes.PET_ACTIVITY_ALREADY_CLAIMED);
        }

        @Test
        @DisplayName("领取成功：经验入账 + 星光经 mall-wish 发放")
        void claimSuccessfully() {
            Pet p = pet();
            when(petService.requireOwnedPet(100L)).thenReturn(p);
            PetActivity completed = new PetActivity();
            completed.setId(11L);
            completed.setActivityType(PetActivityType.WORK.name());
            completed.setStatus(PetActivityStatus.COMPLETED.name());
            completed.setConfigId(9001002L);
            completed.setFinishedAt(LocalDateTime.now(ZoneId.of("UTC")).minusMinutes(5));
            when(activityMapper.selectOne(any())).thenReturn(completed);
            when(activityMapper.update(any(), any())).thenReturn(1);
            when(jobConfigMapper.selectById(9001002L)).thenReturn(job());
            when(stateService.grantExp(eq(p), eq(21))).thenReturn(0);
            when(wishFeignClient.earnStarlight(100L, 105, 11L))
                    .thenReturn(com.cloudmart.common.api.ApiResponse.ok(1200));

            PetActivityVO vo = activityService.claimWork(100L);

            assertThat(vo.status()).isEqualTo(PetActivityStatus.CLAIMED.name());
            org.mockito.Mockito.verify(wishFeignClient).earnStarlight(100L, 105, 11L);
        }

        @Test
        @DisplayName("无任务可领取：404 PET_ACTIVITY_NOT_FOUND")
        void noTask() {
            lenient().when(petService.requireOwnedPet(100L)).thenReturn(pet());
            when(activityMapper.selectOne(any())).thenReturn(null);

            assertThatThrownBy(() -> activityService.claimWork(100L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", PetErrorCodes.PET_ACTIVITY_NOT_FOUND);
        }
    }
}
