package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.wallet.PetEconomyService;
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
    private com.cloudmart.pet.repository.PetEventOccurrenceMapper occurrenceMapper;
    @Mock
    private com.cloudmart.pet.repository.PetEventOccurrenceClaimMapper occurrenceClaimMapper;
    @Mock
    private PetActivityMapper activityMapper;
    @Mock
    private PetBottleRecordMapper bottleRecordMapper;
    @Mock
    private PetBattleMapper battleMapper;
    @Mock
    private PetInventoryMapper inventoryMapper;
    @Mock
    private PetEconomyService economyService;
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
        TableInfoHelper.initTableInfo(assistant, com.cloudmart.pet.entity.PetEventOccurrence.class);
        TableInfoHelper.initTableInfo(assistant, com.cloudmart.pet.entity.PetEventOccurrenceClaim.class);
    }

    @BeforeEach
    void setUp() {
        economyService = org.mockito.Mockito.mock(PetEconomyService.class);
        org.mockito.Mockito.when(economyService.earn(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(Object[].class)))
                .thenReturn(new PetEconomyService.WalletSettlement(0L, 1000L, false));
        eventService = new PetEventServiceImpl(stateService, eventConfigMapper, progressMapper,
                occurrenceMapper,
                occurrenceClaimMapper,
                activityMapper, bottleRecordMapper, battleMapper, inventoryMapper, wishFeignClient,
                economyService, achievementService, eventProducer);
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
    @DisplayName("C15 按期次领奖：进度未达标 → 409 PET_EVENT_NOT_FINISHED")
    void claimOccurrenceNotFinishedRejected() {
        when(occurrenceMapper.selectById(900L)).thenReturn(occurrence(900L, 1));
        when(eventConfigMapper.selectOne(any())).thenReturn(bottleEvent(3));
        when(bottleRecordMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> eventService.claimByOccurrence(100L, 900L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(PetErrorCodes.PET_EVENT_NOT_FINISHED);
    }

    @Test
    @DisplayName("C16：期次缺奖励快照 → fail-closed PET_VALIDATION_ERROR，不按当前配置发奖")
    void claimOccurrenceWithoutSnapshotRejected() {
        com.cloudmart.pet.entity.PetEventOccurrence occurrence = occurrence(900L, 1);
        occurrence.setRewardSnapshot(null);
        when(occurrenceMapper.selectById(900L)).thenReturn(occurrence);
        when(eventConfigMapper.selectOne(any())).thenReturn(bottleEvent(3));

        assertThatThrownBy(() -> eventService.claimByOccurrence(100L, 900L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(PetErrorCodes.PET_VALIDATION_ERROR);
        org.mockito.Mockito.verifyNoInteractions(economyService);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("PET-11/T23：发布期次后修改模板，目标与奖励按发布时快照发放")
    void claimByOccurrenceUsesFrozenSnapshot() {
        com.cloudmart.pet.entity.PetEventOccurrence occurrence = occurrence(900L, 2);
        // 发布时快照：目标 2、星光 999、经验 77（模板后来被改成 target=3/星光 120/经验 40）
        occurrence.setRewardSnapshot("{\"targetValue\":2,\"rewardCoins\":999,\"rewardExp\":77,"
                + "\"rewardItemCode\":\"\",\"rewardAltCoins\":15,\"eventType\":\"BOTTLE\"}");
        when(occurrenceMapper.selectById(900L)).thenReturn(occurrence);
        when(eventConfigMapper.selectOne(any())).thenReturn(bottleEvent(3));
        when(bottleRecordMapper.selectCount(any())).thenReturn(2L);
        when(occurrenceClaimMapper.selectCount(any())).thenReturn(0L);

        PetEventVO vo = eventService.claimByOccurrence(100L, 900L);

        assertThat(vo.targetValue()).isEqualTo(2);
        assertThat(vo.rewardCoins()).isEqualTo(999);
        verify(stateService).grantExp(any(Pet.class), eq(77));
        // 发奖幂等键含 occurrenceId（同日多期不合并）
        org.mockito.Mockito.verify(economyService).earn(
                org.mockito.ArgumentMatchers.eq(100L), org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("EVENT_CLAIM"),
                org.mockito.ArgumentMatchers.eq(999L), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq("bottle_newbie"),
                org.mockito.ArgumentMatchers.eq(900L));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("PET-11/T24：同日两期各自结算一次，替代奖励幂等键不跨期合并")
    void sameDayOccurrencesHaveIndependentRewardKeys() {
        when(eventConfigMapper.selectOne(any())).thenReturn(bottleEvent(3));
        when(bottleRecordMapper.selectCount(any())).thenReturn(3L);
        when(occurrenceClaimMapper.selectCount(any())).thenReturn(0L);
        when(occurrenceMapper.selectById(900L)).thenReturn(occurrence(900L, 1));
        when(occurrenceMapper.selectById(901L)).thenReturn(occurrence(901L, 2));

        eventService.claimByOccurrence(100L, 900L);
        eventService.claimByOccurrence(100L, 901L);

        org.mockito.ArgumentCaptor<Object[]> keys = org.mockito.ArgumentCaptor.forClass(Object[].class);
        org.mockito.Mockito.verify(economyService, org.mockito.Mockito.times(2)).earn(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.eq("EVENT_CLAIM"), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.isNull(),
                keys.capture());
        org.assertj.core.api.Assertions.assertThat(keys.getAllValues())
                .satisfiesExactly(
                        first -> assertThat(first).containsExactly(1L, "bottle_newbie", 900L),
                        second -> assertThat(second).containsExactly(1L, "bottle_newbie", 901L));
    }

    private com.cloudmart.pet.entity.PetEventOccurrence occurrence(Long id, int index) {
        com.cloudmart.pet.entity.PetEventOccurrence occurrence = new com.cloudmart.pet.entity.PetEventOccurrence();
        occurrence.setId(id);
        occurrence.setEventCode("bottle_newbie");
        occurrence.setOccurrenceIndex(index);
        occurrence.setStartAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusDays(3));
        occurrence.setEndAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusHours(1));
        occurrence.setClaimDeadlineAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).plusHours(23));
        occurrence.setStatus("ACTIVE");
        // C16：期次必须带冻结奖励快照（缺快照 fail-closed）；与 bottleEvent(3) 当前配置同口径
        occurrence.setRewardSnapshot("{\"targetValue\":3,\"rewardCoins\":120,\"rewardExp\":40,"
                + "\"rewardItemCode\":\"\",\"rewardAltCoins\":15,\"eventType\":\"BOTTLE\"}");
        return occurrence;
    }

    private PetEventConfig bottleEvent(int target) {
        PetEventConfig config = new PetEventConfig();
        config.setCode("bottle_newbie");
        config.setName("捞瓶新星");
        config.setDescription("累计捞起 3 只漂流瓶");
        config.setEventType("BOTTLE");
        config.setTargetValue(target);
        config.setRewardCoins(120);
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

    @Test
    @org.junit.jupiter.api.DisplayName("R33 按期次领取：进度按期次窗口统计，领奖事实 uk(petId,occurrenceId)")
    void claimByOccurrenceUsesOccurrenceWindow() {
        com.cloudmart.pet.entity.PetEventOccurrence occurrence = occurrence(900L, 2);
        occurrence.setEventCode("BOTTLE_RUSH");
        when(occurrenceMapper.selectById(900L)).thenReturn(occurrence);
        when(eventConfigMapper.selectOne(any())).thenReturn(bottleEvent(3));
        when(bottleRecordMapper.selectCount(any())).thenReturn(3L);
        when(occurrenceClaimMapper.selectCount(any())).thenReturn(0L);

        PetEventVO vo = eventService.claimByOccurrence(100L, 900L);
        org.assertj.core.api.Assertions.assertThat(vo.occurrenceId()).isEqualTo("900");
        org.assertj.core.api.Assertions.assertThat(vo.claimable()).isTrue();
        // 领奖事实写入
        org.mockito.Mockito.verify(occurrenceClaimMapper).insert(
                org.mockito.ArgumentMatchers.any(com.cloudmart.pet.entity.PetEventOccurrenceClaim.class));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("R33 按期次领取：同宠同期重复领取撞 uk → ALREADY_CLAIMED")
    void claimByOccurrenceRejectsDuplicate() {
        com.cloudmart.pet.entity.PetEventOccurrence occurrence = occurrence(900L, 2);
        occurrence.setEventCode("BOTTLE_RUSH");
        when(occurrenceMapper.selectById(900L)).thenReturn(occurrence);
        when(eventConfigMapper.selectOne(any())).thenReturn(bottleEvent(3));
        when(bottleRecordMapper.selectCount(any())).thenReturn(3L);
        org.mockito.Mockito.doThrow(new org.springframework.dao.DuplicateKeyException("uk_occurrence_claim"))
                .when(occurrenceClaimMapper).insert(
                        org.mockito.ArgumentMatchers.any(com.cloudmart.pet.entity.PetEventOccurrenceClaim.class));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> eventService.claimByOccurrence(100L, 900L))
                .isInstanceOf(com.cloudmart.common.exception.BusinessException.class)
                .extracting(e -> ((com.cloudmart.common.exception.BusinessException) e).getCode())
                .isEqualTo(com.cloudmart.pet.constant.PetErrorCodes.PET_EVENT_ALREADY_CLAIMED);
    }
}
