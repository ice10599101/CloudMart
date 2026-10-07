package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetActivity;
import com.cloudmart.pet.feign.WishFeignClient;
import com.cloudmart.pet.mq.PetEventProducer;
import com.cloudmart.pet.repository.PetActivityMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.service.PetAchievementService;
import com.cloudmart.pet.service.PetDailyQuestService;
import com.cloudmart.pet.service.PetIntimacyService;
import com.cloudmart.pet.service.PetRelationService;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.vo.PetVisitResultVO;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 宠物串门测试：不能串自己、精力不足、同邻居冷却、日次数上限、成功结算与邻居提醒。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PetVisitServiceImpl 单元测试")
class PetVisitServiceImplTest {

    @Mock
    private PetService petService;
    @Mock
    private PetStateService stateService;
    @Mock
    private PetMapper petMapper;
    @Mock
    private PetActivityMapper activityMapper;
    @Mock
    private PetAchievementService achievementService;
    @Mock
    private PetEventProducer eventProducer;
    @Mock
    private WishFeignClient wishFeignClient;
    @Mock
    private com.cloudmart.pet.feign.UserFeignClient userFeignClient;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private PetDailyQuestService dailyQuestService;
    @Mock
    private PetIntimacyService intimacyService;
    @Mock
    private PetRelationService relationService;
    @org.mockito.Mock
    private com.cloudmart.pet.service.PetVisitApplicationService visitApplicationServiceMock;

    private PetQuotaService quotaServiceMock;

    private final PetProperties properties = new PetProperties();
    private PetVisitServiceImpl visitService;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PetActivity.class);
        TableInfoHelper.initTableInfo(assistant, Pet.class);
    }

    @BeforeEach
    void setUp() {
        visitApplicationServiceMock = org.mockito.Mockito.mock(com.cloudmart.pet.service.PetVisitApplicationService.class);
        org.mockito.Mockito.lenient().when(visitApplicationServiceMock.recordVisit(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.cloudmart.pet.service.PetVisitApplicationService.VisitGrant(true, true));
        quotaServiceMock = org.mockito.Mockito.mock(PetQuotaService.class);
        // 默认放行（全局日上限非用例关注点；超额用例自行覆盖）
        org.mockito.Mockito.lenient().when(quotaServiceMock.tryConsume(
                        org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(true);
        visitService = new PetVisitServiceImpl(petService, stateService, petMapper, activityMapper,
                achievementService, eventProducer, wishFeignClient, userFeignClient, properties,
                dailyQuestService, intimacyService, relationService,
                org.mockito.Mockito.mock(com.cloudmart.pet.service.PetUserBlockService.class),
                visitApplicationServiceMock,
                quotaServiceMock);
    }

    @Test
    @DisplayName("不能给自己串门：409 PET_VISIT_SELF")
    void selfVisitRejected() {
        when(petService.requireOwnedPet(100L)).thenReturn(pet());
        Pet own = pet();
        own.setId(2L);
        own.setIsPublic(true);
        when(petMapper.selectById(2L)).thenReturn(own);

        assertThatThrownBy(() -> visitService.visit(100L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(PetErrorCodes.PET_VISIT_SELF);
    }

    @Test
    @DisplayName("精力不足：409 PET_VISIT_ENERGY_INSUFFICIENT，且不消耗次数")
    void energyInsufficientRejected() {
        Pet pet = pet();
        pet.setEnergy(3);
        when(petService.requireOwnedPet(100L)).thenReturn(pet);
        when(petMapper.selectById(2L)).thenReturn(neighbor());

        assertThatThrownBy(() -> visitService.visit(100L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(PetErrorCodes.PET_VISIT_ENERGY_INSUFFICIENT);
        verify(valueOperations, never()).increment(anyString());
    }

    @Test
    @DisplayName("同主人同业务日已拜访（事实唯一键冲突）：409 PET_VISIT_COOLDOWN")
    void neighborCooldownRejected() {
        when(petService.requireOwnedPet(100L)).thenReturn(pet());
        when(petMapper.selectById(2L)).thenReturn(neighbor());
        when(visitApplicationServiceMock.recordVisit(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.cloudmart.pet.service.PetVisitApplicationService.VisitGrant(false, false));

        assertThatThrownBy(() -> visitService.visit(100L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(PetErrorCodes.PET_VISIT_COOLDOWN);
    }

    @Test
    @DisplayName("收益额度用尽：拜访仍成立（无收益），不推进经验/亲密度/任务/关系")
    void overQuotaVisitSucceedsWithoutRewards() {
        Pet pet = pet();
        when(petService.requireOwnedPet(100L)).thenReturn(pet);
        when(petMapper.selectById(2L)).thenReturn(neighbor());
        when(visitApplicationServiceMock.recordVisit(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.cloudmart.pet.service.PetVisitApplicationService.VisitGrant(true, false));
        when(activityMapper.insert(any(PetActivity.class))).thenReturn(1);

        PetVisitResultVO result = visitService.visit(100L, 2L);

        assertThat(result.neighborName()).isEqualTo("旺财");
        verify(stateService, org.mockito.Mockito.never()).grantExp(any(), org.mockito.ArgumentMatchers.anyInt());
        verify(intimacyService, org.mockito.Mockito.never()).gain(any(), org.mockito.ArgumentMatchers.any());
        verify(dailyQuestService, org.mockito.Mockito.never()).record(any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
        verify(relationService, org.mockito.Mockito.never()).gainBetween(any(), any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("串门成功：扣精力/加心情/发经验/落留痕，并提醒邻居主人")
    void visitAppliesEffectsAndNotifiesNeighbor() {
        Pet pet = pet();
        when(petService.requireOwnedPet(100L)).thenReturn(pet);
        when(petMapper.selectById(2L)).thenReturn(neighbor());
        when(valueOperations.get(anyString())).thenReturn(null);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(userFeignClient.batchGetUsers(any()))
                .thenReturn(ApiResponse.ok(List.of(Map.of("id", 200L, "nickname", "阿黄主人"))));
        when(activityMapper.insert(any(PetActivity.class))).thenReturn(1);

        PetVisitResultVO result = visitService.visit(100L, 2L);

        PetProperties.Visit cfg = properties.getVisit();
        assertThat(pet.getEnergy()).isEqualTo(100 - cfg.getEnergyCost());
        assertThat(pet.getHappiness()).isEqualTo(60 + cfg.getHappinessGain());
        assertThat(result.neighborName()).isEqualTo("旺财");
        assertThat(result.expGain()).isEqualTo(cfg.getExpGain());
        verify(activityMapper).insert(any(PetActivity.class));
        verify(stateService).grantExp(pet, cfg.getExpGain());

        ArgumentCaptor<PetEventProducer.PetEventMessage> messageCaptor =
                ArgumentCaptor.forClass(PetEventProducer.PetEventMessage.class);
        verify(eventProducer).publishViaOutbox(eq(com.cloudmart.pet.config.RocketMQConfig.PET_TAG_VISIT),
                messageCaptor.capture());
        assertThat(messageCaptor.getValue().userId()).isEqualTo(String.valueOf(200L));
        assertThat(messageCaptor.getValue().reminderType()).isEqualTo("PET_VISIT");
    }

    private Pet pet() {
        Pet pet = new Pet();
        pet.setId(1L);
        pet.setUserId(100L);
        pet.setName("小橘");
        pet.setSpecies("STRAWBERRY");
        pet.setLevel(5);
        pet.setEnergy(100);
        pet.setHappiness(60);
        return pet;
    }

    private Pet neighbor() {
        Pet neighbor = new Pet();
        neighbor.setId(2L);
        neighbor.setUserId(200L);
        neighbor.setName("旺财");
        neighbor.setSpecies("ORANGE");
        neighbor.setLevel(5);
        neighbor.setIsPublic(true);
        return neighbor;
    }
}
