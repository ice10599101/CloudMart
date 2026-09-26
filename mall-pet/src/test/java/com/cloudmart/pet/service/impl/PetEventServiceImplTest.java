package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetActivity;
import com.cloudmart.pet.entity.PetBattle;
import com.cloudmart.pet.entity.PetBottleRecord;
import com.cloudmart.pet.entity.PetEventConfig;
import com.cloudmart.pet.entity.PetEventProgress;
import com.cloudmart.pet.entity.PetInventory;
import com.cloudmart.pet.feign.WishFeignClient;
import com.cloudmart.pet.mq.PetEventProducer;
import com.cloudmart.pet.repository.PetActivityMapper;
import com.cloudmart.pet.repository.PetBattleMapper;
import com.cloudmart.pet.repository.PetBottleRecordMapper;
import com.cloudmart.pet.repository.PetEventConfigMapper;
import com.cloudmart.pet.repository.PetEventProgressMapper;
import com.cloudmart.pet.repository.PetInventoryMapper;
import com.cloudmart.pet.service.PetAchievementService;
import com.cloudmart.pet.vo.PetEventVO;
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

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 社区宠物活动测试：进度惰性统计（COUNT 既有业务表）、未完成/已领/已结束三类拒绝、领奖发奖。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PetEventServiceImpl 单元测试")
class PetEventServiceImplTest {

    @Mock
    private PetStateService stateService;
    @Mock
    private PetEventConfigMapper eventConfigMapper;
    @Mock
    private PetEventProgressMapper progressMapper;
    @Mock
    private PetActivityMapper activityMapper;
    @Mock
    private PetBottleRecordMapper bottleRecordMapper;
    @Mock
    private PetBattleMapper battleMapper;
    @Mock
    private PetInventoryMapper inventoryMapper;
    @Mock
    private PetOperationService operationService;
    @Mock
    private WishFeignClient wishFeignClient;
    @Mock
    private PetAchievementService achievementService;
    @Mock
    private PetEventProducer eventProducer;

    private PetEventServiceImpl eventService;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PetEventConfig.class);
        TableInfoHelper.initTableInfo(assistant, PetEventProgress.class);
        TableInfoHelper.initTableInfo(assistant, PetActivity.class);
        TableInfoHelper.initTableInfo(assistant, PetBottleRecord.class);
        TableInfoHelper.initTableInfo(assistant, PetBattle.class);
        TableInfoHelper.initTableInfo(assistant, PetInventory.class);
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
        eventService = new PetEventServiceImpl(stateService, eventConfigMapper, progressMapper,
                activityMapper, bottleRecordMapper, battleMapper, inventoryMapper, wishFeignClient,
                operationService, achievementService, eventProducer);
        lenient().when(stateService.requireActivePet(100L)).thenReturn(pet());
        lenient().when(stateService.grantExp(any(), any(Integer.class))).thenReturn(0);
    }

    @Test
    @DisplayName("活动列表：捞瓶流水 COUNT 即为进度，达标即可领奖")
    void eventsCountProgressLazily() {
        when(eventConfigMapper.selectList(any())).thenReturn(List.of(bottleEvent(3)));
        when(bottleRecordMapper.selectCount(any())).thenReturn(3L);
        when(progressMapper.selectOne(any())).thenReturn(null);

        List<PetEventVO> events = eventService.events(100L);

        assertThat(events).hasSize(1);
        PetEventVO event = events.getFirst();
        assertThat(event.progress()).isEqualTo(3);
        assertThat(event.completed()).isTrue();
        assertThat(event.claimable()).isTrue();
        assertThat(event.claimed()).isFalse();
    }

    @Test
    @DisplayName("领奖：进度未达标 → 409 PET_EVENT_NOT_FINISHED")
    void claimNotFinishedRejected() {
        when(eventConfigMapper.selectOne(any())).thenReturn(bottleEvent(3));
        when(bottleRecordMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> eventService.claim(100L, "bottle_newbie"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(PetErrorCodes.PET_EVENT_NOT_FINISHED);
    }

    @Test
    @DisplayName("领奖：已领取 → 409 PET_EVENT_ALREADY_CLAIMED")
    void claimAlreadyClaimedRejected() {
        when(eventConfigMapper.selectOne(any())).thenReturn(bottleEvent(3));
        when(bottleRecordMapper.selectCount(any())).thenReturn(3L);
        PetEventProgress claimed = new PetEventProgress();
        claimed.setId(7L);
        claimed.setPetId(1L);
        claimed.setEventCode("bottle_newbie");
        claimed.setProgress(3);
        claimed.setClaimedAt(LocalDateTime.now(ZoneId.of("UTC")));
        when(progressMapper.selectOne(any())).thenReturn(claimed);

        assertThatThrownBy(() -> eventService.claim(100L, "bottle_newbie"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(PetErrorCodes.PET_EVENT_ALREADY_CLAIMED);
    }

    @Test
    @DisplayName("领奖：活动已结束 → 409 PET_EVENT_ENDED")
    void claimEndedEventRejected() {
        PetEventConfig ended = bottleEvent(3);
        ended.setEndsAt(LocalDateTime.now(ZoneId.of("UTC")).minusDays(1));
        when(eventConfigMapper.selectOne(any())).thenReturn(ended);

        assertThatThrownBy(() -> eventService.claim(100L, "bottle_newbie"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(PetErrorCodes.PET_EVENT_ENDED);
    }

    @Test
    @DisplayName("领奖成功：标记领奖 + 经验 + 星光（物品为空则不入包）")
    void claimGrantsRewards() {
        when(eventConfigMapper.selectOne(any())).thenReturn(bottleEvent(3));
        when(bottleRecordMapper.selectCount(any())).thenReturn(3L);
        when(progressMapper.selectOne(any())).thenReturn(null);
        when(progressMapper.insert(any(PetEventProgress.class))).thenReturn(1);

        PetEventVO result = eventService.claim(100L, "bottle_newbie");

        assertThat(result.claimed()).isTrue();
        verify(progressMapper).insert(any(PetEventProgress.class));
        // B01：发薪经统一操作记录
        verify(operationService).executeEarn(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(100L), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("EVENT_CLAIM"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(120), org.mockito.ArgumentMatchers.any());
        verify(stateService).grantExp(any(Pet.class), eq(40));
    }

    private PetEventConfig bottleEvent(int target) {
        PetEventConfig config = new PetEventConfig();
        config.setCode("bottle_newbie");
        config.setName("捞瓶新星");
        config.setDescription("累计捞起 3 只漂流瓶");
        config.setEventType("BOTTLE");
        config.setTargetValue(target);
        config.setRewardStarlight(120);
        config.setRewardExp(40);
        config.setEnabled(true);
        return config;
    }

    private Pet pet() {
        Pet pet = new Pet();
        pet.setId(1L);
        pet.setUserId(100L);
        pet.setName("小橘");
        pet.setLevel(5);
        return pet;
    }
}
