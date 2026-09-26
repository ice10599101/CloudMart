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
    }

    @BeforeEach
    void setUp() {
        properties = new PetProperties();
        // 真实 PetClock + 固定 Clock：businessDate/businessDateOf 走真实换算
        java.time.Clock fixed = java.time.Clock.fixed(now.atOffset(java.time.ZoneOffset.UTC).toInstant(),
                java.time.ZoneOffset.UTC);
        petClock = new PetClock(fixed, properties);
        intimacyService = new PetIntimacyServiceImpl(petMapper, sessionMapper, dailyMapper,
                properties, eventProducer, outboxService, petClock);
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
    @DisplayName("概览：当天未发心跳也显示正确的今日值（读业务日行）")
    void overviewShowsCorrectTodayValue() {
        stubPet(pet());
        when(dailyMapper.selectOne(any())).thenReturn(daily(1200, 2));

        var vo = intimacyService.overview(100L);

        assertThat(vo.todayCompanionSeconds()).isEqualTo(1200);
        assertThat(vo.intimacy()).isZero();
    }
}
