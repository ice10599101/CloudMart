package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.pet.config.PetClock;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.repository.PetActivityMapper;
import com.cloudmart.pet.repository.PetAlbumAssetMapper;
import com.cloudmart.pet.repository.PetCooperationMapper;
import com.cloudmart.pet.repository.PetCompanionDailyPetMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.repository.PetMinigameRoundMapper;
import com.cloudmart.pet.repository.PetPurchaseOrderMapper;
import com.cloudmart.pet.service.PetDailyQuestService;
import com.cloudmart.pet.service.PetEventService;
import com.cloudmart.pet.vo.PetDailyQuestVO;
import com.cloudmart.pet.wallet.PetWalletQueryService;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R31：bootstrap / activity-center 聚合读请求级去重单元测试。
 *
 * <p>修复前：/bootstrap 产生 8 次 pet_daily_quota 单行查询 + 主宠/快照重复查 pet；
 * /activity-center 主宠被 3 处各自加载。修复后同源复用 + IN 批量读，本类以
 * mapper 调用计数作为去重的实质性回归断言（与 PetPveServiceImplTest 同模式）。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PetBootstrapService 聚合去重单元测试")
class PetBootstrapServiceTest {

    @Mock private PetMapper petMapper;
    @Mock private PetClock petClock;
    @Mock private PetActivityMutex activityMutex;
    @Mock private PetQuotaService quotaService;
    @Mock private PetActivityMapper activityMapper;
    @Mock private PetCooperationMapper cooperationMapper;
    @Mock private PetWalletQueryService walletQueryService;
    @Mock private PetStateService stateService;
    @Mock private PetDailyQuestService questService;
    @Mock private PetCompanionDailyPetMapper companionDailyPetMapper;
    @Mock private PetEventService eventService;
    @Mock private PetAlbumAssetMapper albumAssetMapper;
    @Mock private PetMinigameRoundMapper minigameRoundMapper;
    @Mock private PetPurchaseOrderMapper purchaseOrderMapper;

    private PetBootstrapService bootstrapService;

    private static final Long USER_ID = 100L;
    private static final Long PET_ID = 7L;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Pet.class);
    }

    @BeforeEach
    void setUp() {
        bootstrapService = new PetBootstrapService(petMapper, petClock, new PetProperties(),
                activityMutex, quotaService, activityMapper, cooperationMapper, walletQueryService,
                stateService, questService, companionDailyPetMapper, eventService,
                albumAssetMapper, minigameRoundMapper, purchaseOrderMapper);

        when(petClock.businessDate()).thenReturn(LocalDate.of(2026, 10, 14));
        when(petClock.nowUtc()).thenReturn(LocalDateTime.of(2026, 10, 14, 12, 0));
        when(petClock.businessDateStartUtc(any())).thenReturn(LocalDateTime.of(2026, 10, 15, 0, 0));

        when(activityMutex.hasBusyActivity(anyLong())).thenReturn(false);
        when(activityMutex.hasActiveCustody(anyLong())).thenReturn(false);
        when(activityMutex.hasActiveMinigameRound(anyLong())).thenReturn(false);
        when(activityMapper.selectCount(any())).thenReturn(0L);
        when(cooperationMapper.selectCount(any())).thenReturn(0L);
        when(albumAssetMapper.selectCount(any())).thenReturn(0L);
        when(minigameRoundMapper.selectCount(any())).thenReturn(0L);
        when(purchaseOrderMapper.selectCount(any())).thenReturn(0L);
        when(stateService.isWeak(any())).thenReturn(false);
        when(stateService.isSick(any())).thenReturn(false);
    }

    private Pet activePet() {
        Pet pet = new Pet();
        pet.setId(PET_ID);
        pet.setUserId(USER_ID);
        pet.setName("奶油");
        pet.setLevel(3);
        pet.setIsActive(true);
        pet.setIntimacy(50);
        pet.setCompanionSeconds(120L);
        pet.setVersion(0);
        return pet;
    }

    private EnumMap<PetQuotaService.QuotaType, Integer> zeroQuotas() {
        EnumMap<PetQuotaService.QuotaType, Integer> used =
                new EnumMap<>(PetQuotaService.QuotaType.class);
        List.of(PetQuotaService.QuotaType.FEED, PetQuotaService.QuotaType.PLAY_REWARD,
                        PetQuotaService.QuotaType.WALL_POST, PetQuotaService.QuotaType.REPORT)
                .forEach(type -> used.put(type, 0));
        return used;
    }

    @Test
    @DisplayName("bootstrap：宠物列表只查 1 次且无 selectById；配额只批量读 1 次，不再逐类型 remaining")
    void bootstrap_LoadsPetListOnceAndQuotasInBatch() {
        when(petMapper.selectList(any())).thenReturn(List.of(activePet()));
        when(quotaService.usedBatch(anyLong(), any(), anyLong())).thenReturn(zeroQuotas());

        Map<String, Object> result = bootstrapService.bootstrap(USER_ID, null);

        assertThat(result.get("activePetId")).isEqualTo(PET_ID);
        assertThat(result.get("selectedPet")).isNotNull();

        verify(petMapper, times(1)).selectList(any());
        verify(petMapper, never()).selectById(any());
        verify(petMapper, never()).selectOne(any());
        verify(quotaService, times(1)).usedBatch(anyLong(), any(), anyLong());
        verify(quotaService, never()).remaining(anyLong(), any(), anyLong(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("activity-center 有主宠：主宠只加载 1 次并透传给 event/quest，无 selectById")
    void activityCenter_WithPet_ReusesSettledPetAcrossAggregates() {
        Pet pet = activePet();
        when(stateService.findActivePet(USER_ID)).thenReturn(pet);
        when(activityMapper.selectOne(any())).thenReturn(null);
        when(cooperationMapper.selectOne(any())).thenReturn(null);
        when(eventService.eventsForPet(pet)).thenReturn(List.of());
        when(questService.listForPet(pet)).thenReturn(
                new PetDailyQuestVO(LocalDate.of(2026, 10, 14), List.of(),
                        1, 0, 3, false, false, null, null, null));

        Map<String, Object> result = bootstrapService.activityCenter(USER_ID, null);

        assertThat(result.get("selectedPetActivity")).isNull();
        assertThat(result.get("eventSummary")).isNotNull();
        assertThat(result.get("dailySetSummary")).isNotNull();

        verify(stateService, times(1)).findActivePet(USER_ID);
        verify(petMapper, never()).selectById(any());
        verify(eventService, times(1)).eventsForPet(pet);
        verify(eventService, never()).events(anyLong());
        verify(questService, times(1)).listForPet(pet);
        verify(questService, never()).list(anyLong());
    }

    @Test
    @DisplayName("activity-center 无主宠：各子聚合降级 null，不调用 event/quest，不查宠物")
    void activityCenter_WithoutPet_DegradesToNulls() {
        when(stateService.findActivePet(USER_ID)).thenReturn(null);
        when(cooperationMapper.selectOne(any())).thenReturn(null);

        Map<String, Object> result = bootstrapService.activityCenter(USER_ID, null);

        assertThat(result.get("selectedPetActivity")).isNull();
        assertThat(result.get("eventSummary")).isNull();
        assertThat(result.get("dailySetSummary")).isNull();

        verify(stateService, times(1)).findActivePet(USER_ID);
        verify(petMapper, never()).selectList(any());
        verify(petMapper, never()).selectById(any());
        verify(eventService, never()).eventsForPet(any());
        verify(questService, never()).listForPet(any());
    }

    @Test
    @DisplayName("activity-center 显式 petId：保留 selectById 分支以支持查看本人非主宠")
    void activityCenter_ExplicitPetId_StillLoadsById() {
        Pet inactivePet = activePet();
        inactivePet.setId(9L);
        inactivePet.setIsActive(false);
        when(stateService.findActivePet(USER_ID)).thenReturn(activePet());
        when(petMapper.selectById(9L)).thenReturn(inactivePet);
        when(activityMapper.selectOne(any())).thenReturn(null);
        when(cooperationMapper.selectOne(any())).thenReturn(null);
        when(eventService.eventsForPet(any())).thenReturn(List.of());
        when(questService.listForPet(any())).thenReturn(
                new PetDailyQuestVO(LocalDate.of(2026, 10, 14), List.of(),
                        0, 0, 3, false, false, null, null, null));

        Map<String, Object> result = bootstrapService.activityCenter(USER_ID, 9L);

        assertThat(result).containsKey("selectedPetActivity");
        verify(petMapper, times(1)).selectById(9L);
    }
}
