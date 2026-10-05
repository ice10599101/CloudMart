package com.cloudmart.pet.service.impl;

import com.cloudmart.pet.config.PetClock;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetCompanionDaily;
import com.cloudmart.pet.entity.PetCompanionSession;
import com.cloudmart.pet.vo.PetCompanionSessionVO;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B05 陪伴会话/积分/亲密度单元测试（固定 Clock，不依赖真实等待）。
 *
 * <p>核心验收（任务书 B05）：服务端会话计时权威、伪造秒数不加速；重复心跳按序号幂等；
 * 会话失效不补计中断区间；正常停止只结算有效窗口；概览今日值正确。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PetIntimacyServiceImplTest {

    @Mock
    private com.cloudmart.pet.repository.PetMapper petMapper;
    @Mock
    private com.cloudmart.pet.repository.PetCompanionSessionMapper sessionMapper;
    @Mock
    private com.cloudmart.pet.repository.PetCompanionDailyMapper dailyMapper;
    @Mock
    private com.cloudmart.pet.mq.PetEventProducer eventProducer;
    @Mock
    private PetOutboxService outboxService;

    private PetIntimacyServiceImpl intimacyService;
    private PetProperties properties;
    private PetClock petClock;

    /** 固定业务时刻（UTC），用例内手动推进 */
    private final LocalDateTime now = LocalDateTime.of(2026, 9, 26, 2, 0, 0);

    @BeforeAll
    static void initEntityMeta() {
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, Pet.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, PetCompanionSession.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, PetCompanionDaily.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant,
                com.cloudmart.pet.entity.PetCompanionDailyPet.class);
    }

    @BeforeEach
    void setUp() {
        properties = new PetProperties();
        // 真实 PetClock + 固定 Clock：businessDate/businessDateOf 走真实换算
        java.time.Clock fixed = java.time.Clock.fixed(now.atOffset(java.time.ZoneOffset.UTC).toInstant(),
                java.time.ZoneOffset.UTC);
        petClock = new PetClock(fixed, properties);
        com.cloudmart.pet.service.PetUserGuardService guardService =
                org.mockito.Mockito.mock(com.cloudmart.pet.service.PetUserGuardService.class);
        org.mockito.Mockito.lenient().when(guardService.lockGuard(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.cloudmart.pet.entity.PetUserGuard());
        intimacyService = new PetIntimacyServiceImpl(petMapper, sessionMapper, dailyMapper,
                org.mockito.Mockito.mock(com.cloudmart.pet.repository.PetCompanionDailyPetMapper.class),
                properties, eventProducer, outboxService, org.mockito.Mockito.mock(com.cloudmart.pet.service.PetAchievementService.class), petClock, guardService,
                // R36：COMPANION 任务事件桩——捕获调用供断言（getIfAvailable 返回 null 也不抛）
                new org.springframework.beans.factory.ObjectProvider<com.cloudmart.pet.service.PetDailyQuestService>() {
                    @Override
                    public com.cloudmart.pet.service.PetDailyQuestService getObject(Object... args) {
                        return null;
                    }

                    @Override
                    public com.cloudmart.pet.service.PetDailyQuestService getIfAvailable() {
                        return null;
                    }

                    @Override
                    public com.cloudmart.pet.service.PetDailyQuestService getIfUnique() {
                        return null;
                    }

                    @Override
                    public java.util.stream.Stream<com.cloudmart.pet.service.PetDailyQuestService> stream() {
                        return java.util.stream.Stream.empty();
                    }
                });
        lenient().when(sessionMapper.insert(any(PetCompanionSession.class))).thenReturn(1);
        lenient().when(dailyMapper.insert(any(PetCompanionDaily.class))).thenReturn(1);
        lenient().when(petMapper.update(any(), any())).thenReturn(1);
    }

    private Pet pet() {
        Pet pet = new Pet();
        pet.setId(1L);
        pet.setUserId(100L);
        pet.setName("小橘");
        pet.setIntimacy(0);
        pet.setLevel(1);
        return pet;
    }

    private void stubPet(Pet pet) {
        when(petMapper.selectOne(any())).thenReturn(pet);
    }

    private void stubActiveSession(PetCompanionSession session) {
        when(sessionMapper.selectOne(any())).thenReturn(session);
    }

    private PetCompanionSession activeSession(long lastSeq, LocalDateTime lastHeartbeat) {
        PetCompanionSession session = new PetCompanionSession();
        session.setId(9L);
        session.setUserId(100L);
        session.setPetId(1L);
        session.setStatus("ACTIVE");
        session.setStartedAt(lastHeartbeat);
        session.setLastHeartbeatAt(lastHeartbeat);
        session.setLastSeq(lastSeq);
        return session;
    }

    private PetCompanionDaily daily(int acceptedSeconds, int grantedPoints) {
        PetCompanionDaily row = new PetCompanionDaily();
        row.setId(77L);
        row.setUserId(100L);
        row.setBusinessDate(LocalDate.of(2026, 9, 26));
        row.setAcceptedSeconds(acceptedSeconds);
        row.setGrantedPoints(grantedPoints);
        return row;
    }

    @Test
    @DisplayName("首次心跳建立基准：不凭空增加时长，accepted 不增长")
    void firstHeartbeatEstablishesBaseline() {
        stubPet(pet());
        stubActiveSession(null);

        PetCompanionSessionVO vo = intimacyService.heartbeat(100L, 60, 1L);

        assertThat(vo.accepted()).isFalse();
        assertThat(vo.creditedSeconds()).isZero();
        org.mockito.Mockito.verify(sessionMapper).insert(any(PetCompanionSession.class));
        assertThat(vo.todayAcceptedSeconds()).isZero();
    }

    @Test
    @DisplayName("伪造秒数不加速：客户端上报 600 秒仍按服务端时钟差 60 秒计入")
    void forgedSecondsDoNotAccelerate() {
        stubPet(pet());
        stubActiveSession(activeSession(0L, now.minusSeconds(60)));
        when(dailyMapper.selectOne(any())).thenReturn(daily(0, 0));

        PetCompanionSessionVO vo = intimacyService.heartbeat(100L, 600, 1L);

        assertThat(vo.accepted()).isTrue();
        assertThat(vo.creditedSeconds()).isEqualTo(60);
    }

    @Test
    @DisplayName("重复心跳幂等：seq 不前进直接返回，不重复计时")
    void duplicateHeartbeatIgnored() {
        stubPet(pet());
        stubActiveSession(activeSession(5L, now.minusSeconds(60)));
        when(dailyMapper.selectOne(any())).thenReturn(daily(300, 0));

        PetCompanionSessionVO vo = intimacyService.heartbeat(100L, 60, 5L);

        assertThat(vo.accepted()).isFalse();
        assertThat(vo.creditedSeconds()).isZero();
    }

    @Test
    @DisplayName("会话失效不补计中断区间：超过 90 秒无心跳，重建基准且 credited=0")
    void expiredSessionDoesNotBackfill() {
        stubPet(pet());
        stubActiveSession(activeSession(1L, now.minusSeconds(300)));
        when(dailyMapper.selectOne(any())).thenReturn(daily(0, 0));

        PetCompanionSessionVO vo = intimacyService.heartbeat(100L, 300, 2L);

        assertThat(vo.accepted()).isFalse();
        assertThat(vo.creditedSeconds()).isZero();
        // 旧会话被结束（EXPIRED），新会话建立
        verify(sessionMapper, atLeastOnce()).update(any(), any());
    }

    @Test
    @DisplayName("正常停止只结算有效窗口内时间（30 秒窗口）")
    void stopSettlesValidWindow() {
        stubPet(pet());
        stubActiveSession(activeSession(3L, now.minusSeconds(30)));
        // 收益结算按会话绑定宠物（BE-03）
        org.mockito.Mockito.lenient().when(petMapper.selectById(1L)).thenReturn(pet());
        when(dailyMapper.selectOne(any())).thenReturn(daily(570, 0));

        PetCompanionSessionVO vo = intimacyService.stopSession(100L);

        assertThat(vo.accepted()).isTrue();
        assertThat(vo.creditedSeconds()).isEqualTo(30);
    }

    @Test
    @DisplayName("亲密度经验加成：等级阈值 1200 → 第 4 档，加成 3%")
    void expBonusThresholds() {
        Pet pet = pet();
        pet.setIntimacy(1200);
        assertThat(intimacyService.levelOf(1200)).isEqualTo(4);
        assertThat(intimacyService.expBonus(pet)).isCloseTo(0.03, within(1e-9));
    }

    @Test
    @DisplayName("PET-15/T37：跨午夜心跳分两日入账（23:59:30～00:00:30 分属两日）")
    void crossMidnightHeartbeatSplitsByBusinessDate() {
        stubPet(pet());
        // 业务日边界前后各 30 秒：边界由 PetClock 换算（businessDateStartUtc），固定时钟落在边界+30s
        LocalDateTime boundaryUtc = petClock.businessDateStartUtc(LocalDate.of(2026, 9, 26));
        LocalDate prevDate = petClock.businessDateOf(boundaryUtc.minusSeconds(30));
        LocalDate curDate = petClock.businessDateOf(boundaryUtc.plusSeconds(30));
        org.assertj.core.api.Assertions.assertThat(prevDate).isNotEqualTo(curDate);

        PetClock boundaryClock = new PetClock(java.time.Clock.fixed(
                boundaryUtc.plusSeconds(30).atOffset(java.time.ZoneOffset.UTC).toInstant(),
                java.time.ZoneOffset.UTC), properties);
        stubActiveSession(activeSession(1L, boundaryUtc.minusSeconds(30)));
        when(dailyMapper.selectOne(any())).thenReturn(null);
        var petDailyMapper = org.mockito.Mockito.mock(com.cloudmart.pet.repository.PetCompanionDailyPetMapper.class);
        org.mockito.Mockito.lenient().when(petDailyMapper.selectOne(any())).thenReturn(null);
        when(petDailyMapper.insert(any(com.cloudmart.pet.entity.PetCompanionDailyPet.class))).thenReturn(1);
        reinstate(boundaryClock, petDailyMapper);

        PetCompanionSessionVO vo = intimacyService.heartbeat(100L, 60, 2L);

        assertThat(vo.creditedSeconds()).isEqualTo(60);
        org.mockito.ArgumentCaptor<PetCompanionDaily> dailyRows =
                org.mockito.ArgumentCaptor.forClass(PetCompanionDaily.class);
        org.mockito.Mockito.verify(dailyMapper, org.mockito.Mockito.times(2))
                .insert(dailyRows.capture());
        org.assertj.core.api.Assertions.assertThat(dailyRows.getAllValues())
                .extracting(PetCompanionDaily::getBusinessDate)
                .containsExactly(prevDate, curDate);
    }

    @Test
    @DisplayName("PET-15/T37：qualifiedDay 首次达标当日才增加累计/连续天数，已达标日不重复增加")
    void lifetimeCountersOnlyOnQualifiedTransition() {
        stubPet(pet());
        stubActiveSession(activeSession(1L, now.minusSeconds(60)));
        when(dailyMapper.selectOne(any())).thenReturn(daily(0, 0));
        // 本宠日账：已有 30 秒未达标（阈值默认 60 秒）
        com.cloudmart.pet.entity.PetCompanionDailyPet ledger =
                new com.cloudmart.pet.entity.PetCompanionDailyPet();
        ledger.setId(5L);
        ledger.setPetId(1L);
        ledger.setBusinessDate(LocalDate.of(2026, 9, 26));
        ledger.setAcceptedSeconds(30);
        ledger.setQualifiedDay(0);
        reinstateWithLedger(ledger, 0);

        intimacyService.heartbeat(100L, 60, 2L);

        // 30 + 60 = 90 >= 60 首次达标 → companion_days 增长
        org.assertj.core.api.Assertions.assertThat(petUpdatesWithSql("companion_days")).isEqualTo(1);
    }

    @Test
    @DisplayName("PET-15/T37：已达标日再次心跳不重复增加天数")
    void lifetimeCountersNotDuplicatedAfterQualified() {
        stubPet(pet());
        stubActiveSession(activeSession(1L, now.minusSeconds(60)));
        when(dailyMapper.selectOne(any())).thenReturn(daily(0, 0));
        com.cloudmart.pet.entity.PetCompanionDailyPet ledger =
                new com.cloudmart.pet.entity.PetCompanionDailyPet();
        ledger.setId(5L);
        ledger.setPetId(1L);
        ledger.setBusinessDate(LocalDate.of(2026, 9, 26));
        ledger.setAcceptedSeconds(300);
        ledger.setQualifiedDay(1);
        reinstateWithLedger(ledger, 1);

        intimacyService.heartbeat(100L, 60, 2L);

        assertThat(petUpdatesWithSql("companion_days")).isZero();
    }

    /** 统计 petMapper.update 中 SQL 集合包含指定列片段的调用次数 */
    private int petUpdatesWithSql(String fragment) {
        org.mockito.ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Pet>> captor =
                org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        org.mockito.Mockito.verify(petMapper, org.mockito.Mockito.atLeast(0))
                .update(org.mockito.ArgumentMatchers.isNull(), captor.capture());
        return (int) captor.getAllValues().stream()
                .filter(w -> String.valueOf(w.getSqlSet()).contains(fragment))
                .count();
    }

    /** 用给定 petDailyMapper 重建服务（其余依赖沿用 setUp 桩） */
    private void reinstateWithPetDaily(com.cloudmart.pet.repository.PetCompanionDailyPetMapper mapper) {
        reinstate(petClock, mapper);
    }

    /** 用给定时钟与 petDailyMapper 重建服务（其余依赖沿用 setUp 桩） */
    private void reinstate(PetClock clock, com.cloudmart.pet.repository.PetCompanionDailyPetMapper mapper) {
        com.cloudmart.pet.service.PetUserGuardService guardService =
                org.mockito.Mockito.mock(com.cloudmart.pet.service.PetUserGuardService.class);
        org.mockito.Mockito.lenient().when(guardService.lockGuard(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.cloudmart.pet.entity.PetUserGuard());
        intimacyService = new PetIntimacyServiceImpl(petMapper, sessionMapper, dailyMapper,
                mapper, properties, eventProducer, outboxService,
                org.mockito.Mockito.mock(com.cloudmart.pet.service.PetAchievementService.class), clock,
                guardService, nullProvider());
    }

    /** 用给定本宠日账行重建服务（selectOne 返回该行） */
    private void reinstateWithLedger(com.cloudmart.pet.entity.PetCompanionDailyPet ledger, int qualifiedDay) {
        var mapper = org.mockito.Mockito.mock(com.cloudmart.pet.repository.PetCompanionDailyPetMapper.class);
        org.mockito.Mockito.lenient().when(mapper.selectOne(any())).thenReturn(ledger);
        org.mockito.Mockito.lenient().when(mapper.update(any(), any())).thenReturn(1);
        reinstateWithPetDaily(mapper);
    }

    private org.springframework.beans.factory.ObjectProvider<com.cloudmart.pet.service.PetDailyQuestService> nullProvider() {
        return new org.springframework.beans.factory.ObjectProvider<>() {
            @Override
            public com.cloudmart.pet.service.PetDailyQuestService getObject(Object... args) {
                return null;
            }

            @Override
            public com.cloudmart.pet.service.PetDailyQuestService getIfAvailable() {
                return null;
            }

            @Override
            public com.cloudmart.pet.service.PetDailyQuestService getIfUnique() {
                return null;
            }

            @Override
            public java.util.stream.Stream<com.cloudmart.pet.service.PetDailyQuestService> stream() {
                return java.util.stream.Stream.empty();
            }
        };
    }

    @Test
    @DisplayName("概览：当天未发心跳也显示正确的今日值（读业务日行）")
    void overviewShowsCorrectTodayValue() {
        stubPet(pet());
        when(dailyMapper.selectOne(any())).thenReturn(daily(1200, 2));

        var vo = intimacyService.overview(100L);

        assertThat(vo.todayCompanionSeconds()).isEqualTo(1200);
        assertThat(vo.intimacy()).isZero();
    }
}
