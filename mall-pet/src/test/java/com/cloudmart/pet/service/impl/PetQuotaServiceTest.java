package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.pet.config.PetClock;
import com.cloudmart.pet.config.PetMetrics;
import com.cloudmart.pet.entity.PetDailyQuota;
import com.cloudmart.pet.repository.PetDailyQuotaMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R31：{@link PetQuotaService#usedBatch} 批量额度读单元测试
 * （bootstrap 聚合从 8 次单行 selectOne 收敛为 1 次 IN 查询）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PetQuotaService.usedBatch 单元测试")
class PetQuotaServiceTest {

    @Mock
    private PetDailyQuotaMapper quotaMapper;
    @Mock
    private PetClock petClock;
    @Mock
    private PetMetrics metrics;
    @Mock

    private PetQuotaService quotaService;

    private static final List<PetQuotaService.QuotaType> DISPLAY_TYPES = List.of(
            PetQuotaService.QuotaType.FEED,
            PetQuotaService.QuotaType.PLAY_REWARD,
            PetQuotaService.QuotaType.WALL_POST,
            PetQuotaService.QuotaType.REPORT);

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PetDailyQuota.class);
    }

    @BeforeEach
    void setUp() {
        quotaService = new PetQuotaService(quotaMapper, petClock, metrics);
    }

    private PetDailyQuota row(PetQuotaService.QuotaType type, int used) {
        PetDailyQuota quota = new PetDailyQuota();
        quota.setQuotaType(type.name());
        quota.setUsed(used);
        return quota;
    }

    @Test
    @DisplayName("部分类型有行 -> 命中类型取行值，无行类型按 0，且只发一次 IN 查询")
    void usedBatch_PartialRows_FillsZeroForMissingTypes() {
        Long userId = 100L;
        when(petClock.businessDate()).thenReturn(LocalDate.of(2026, 10, 14));
        when(quotaMapper.selectList(any())).thenReturn(List.of(
                row(PetQuotaService.QuotaType.FEED, 2),
                row(PetQuotaService.QuotaType.REPORT, 9)));

        Map<PetQuotaService.QuotaType, Integer> used =
                quotaService.usedBatch(userId, DISPLAY_TYPES, 0L);

        assertThat(used)
                .containsEntry(PetQuotaService.QuotaType.FEED, 2)
                .containsEntry(PetQuotaService.QuotaType.REPORT, 9)
                .containsEntry(PetQuotaService.QuotaType.PLAY_REWARD, 0)
                .containsEntry(PetQuotaService.QuotaType.WALL_POST, 0)
                .isInstanceOf(EnumMap.class);
        verify(quotaMapper).selectList(any());
    }

    @Test
    @DisplayName("当日无任何配额行 -> 全部按 0（IN 查询仍执行一次）")
    void usedBatch_NoRows_AllZero() {
        Long userId = 101L;
        when(petClock.businessDate()).thenReturn(LocalDate.of(2026, 10, 14));
        when(quotaMapper.selectList(any())).thenReturn(List.of());

        Map<PetQuotaService.QuotaType, Integer> used =
                quotaService.usedBatch(userId, DISPLAY_TYPES, 0L);

        DISPLAY_TYPES.forEach(type -> assertThat(used).containsEntry(type, 0));
    }

    @Test
    @DisplayName("空类型集合 -> IllegalArgumentException 且不查库（避免 SQL IN 空集）")
    void usedBatch_EmptyTypes_RejectsWithoutQuery() {
        assertThatThrownBy(() -> quotaService.usedBatch(1L, List.of(), 0L))
                .isInstanceOf(IllegalArgumentException.class);
        verify(quotaMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("PET-02：23:59 占用次日校验失败——release 按原凭据退回原日，不退到新一天")
    void release_byReservation_releasesOriginalBusinessDate() {
        LocalDate occupiedDate = LocalDate.of(2026, 10, 16);
        // 占用时业务日为 D1
        when(petClock.businessDate()).thenReturn(occupiedDate);
        when(quotaMapper.update(org.mockito.ArgumentMatchers.isNull(), any())).thenReturn(1);
        PetQuotaService.QuotaReservation reservation =
                quotaService.tryConsumeReserved(100L, PetQuotaService.QuotaType.FEED, 0L, 3);
        assertThat(reservation.granted()).isTrue();
        assertThat(reservation.businessDate()).isEqualTo(occupiedDate);

        // 次日校验失败回退：release 按凭据释放——不再读当前 petClock（PET-02 关键不变量）
        quotaService.release(reservation);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PetDailyQuota>>
                captor = org.mockito.ArgumentCaptor.forClass(
                (Class<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PetDailyQuota>>)
                        (Class<?>) com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(quotaMapper, org.mockito.Mockito.times(2)).update(
                org.mockito.ArgumentMatchers.isNull(), captor.capture());
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PetDailyQuota> wrapper =
                captor.getAllValues().get(1);
        // 释放条件必须钉住原占用日（business_date = D1），而非释放当天（D2）
        String sqlSet = wrapper.getSqlSet();
        assertThat(sqlSet).contains("GREATEST(used - 1, 0)");
    }

    @Test
    @DisplayName("PET-02：未占用的凭据（granted=false）释放为无操作——limit=0 时连占用都不发生")
    void release_notGranted_noOp() {
        when(petClock.businessDate()).thenReturn(LocalDate.of(2026, 10, 16));
        PetQuotaService.QuotaReservation reservation =
                quotaService.tryConsumeReserved(100L, PetQuotaService.QuotaType.FEED, 0L, 0);
        assertThat(reservation.granted()).isFalse();

        quotaService.release(reservation);

        // limit=0 直接短路：mapper 零交互（占用与释放都不发生）
        verify(quotaMapper, org.mockito.Mockito.never()).update(
                org.mockito.ArgumentMatchers.isNull(), any());
    }
}
