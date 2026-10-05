package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.pet.config.PetClock;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.entity.PetCooperation;
import com.cloudmart.pet.entity.PetCooperationContribution;
import com.cloudmart.pet.entity.PetCooperationMember;
import com.cloudmart.pet.entity.PetFriend;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PET-16/T39：合作贡献锁顺序——双方守卫按 userId 数值升序（与调用方身份无关），
 * 修复原实现"邀请者 A→B、被邀请者 B→A"的死锁条件；锁内重读队伍，已退出/已流转
 * 的队伍不再吸收贡献。
 */
@DisplayName("合作贡献锁顺序测试（PET-16/T39）")
class PetCooperationServiceTest {

    private com.cloudmart.pet.repository.PetMapper petMapper;
    private com.cloudmart.pet.repository.PetCooperationMapper cooperationMapper;
    private com.cloudmart.pet.repository.PetCooperationContributionMapper contributionMapper;
    private com.cloudmart.pet.service.PetUserGuardService guardService;
    private PetCooperationService service;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PetCooperation.class);
        TableInfoHelper.initTableInfo(assistant, PetCooperationContribution.class);
        TableInfoHelper.initTableInfo(assistant, PetCooperationMember.class);
        TableInfoHelper.initTableInfo(assistant, PetFriend.class);
    }

    @BeforeEach
    void setUp() {
        petMapper = mock(com.cloudmart.pet.repository.PetMapper.class);
        cooperationMapper = mock(com.cloudmart.pet.repository.PetCooperationMapper.class);
        contributionMapper = mock(com.cloudmart.pet.repository.PetCooperationContributionMapper.class);
        guardService = mock(com.cloudmart.pet.service.PetUserGuardService.class);
        PetProperties properties = new PetProperties();
        PetClock clock = new PetClock(java.time.Clock.fixed(
                LocalDateTime.of(2026, 9, 30, 2, 0).atOffset(java.time.ZoneOffset.UTC).toInstant(),
                java.time.ZoneOffset.UTC), properties);
        service = new PetCooperationService(
                petMapper, clock, cooperationMapper, contributionMapper,
                mock(com.cloudmart.pet.repository.PetCooperationMemberMapper.class),
                mock(com.cloudmart.pet.repository.PetCollectionEntryMapper.class),
                mock(com.cloudmart.pet.repository.PetCollectionRecordMapper.class),
                properties,
                mock(com.cloudmart.pet.repository.PetInventoryMapper.class),
                mock(com.cloudmart.pet.wallet.PetEconomyService.class),
                guardService,
                mock(com.cloudmart.pet.service.PetUserBlockService.class),
                mock(com.cloudmart.pet.repository.PetFriendMapper.class),
                mock(com.cloudmart.pet.repository.PetRewardClaimMapper.class),
                mock(PetAccessPolicy.class));
    }

    private PetCooperation activeCooperation() {
        PetCooperation cooperation = new PetCooperation();
        cooperation.setId(9L);
        cooperation.setWeekStart(LocalDate.of(2026, 9, 28));
        cooperation.setInviterUserId(22L);
        cooperation.setInviteeUserId(11L);
        cooperation.setStatus("ACTIVE");
        return cooperation;
    }

    @Test
    @DisplayName("T39：邀请者调用时锁序为 userId 升序（11→22），不产生 A→B/B→A 死锁方向")
    void lockOrderAscendingWhenInviterCalls() {
        when(cooperationMapper.selectOne(any())).thenReturn(activeCooperation());
        when(contributionMapper.insert(any(PetCooperationContribution.class))).thenReturn(1);
        when(contributionMapper.selectCount(any())).thenReturn(0L);

        service.recordContribution(22L, "FEED:1:2026-09-30");

        ArgumentCaptor<Long> locks = ArgumentCaptor.forClass(Long.class);
        verify(guardService, org.mockito.Mockito.atLeastOnce()).lockGuard(locks.capture());
        List<Long> order = locks.getAllValues();
        assertThat(order).containsExactly(11L, 22L);
    }

    @Test
    @DisplayName("T39：被邀请者调用时锁序同为 userId 升序（11→22），双方向一致无死锁")
    void lockOrderAscendingWhenInviteeCalls() {
        when(cooperationMapper.selectOne(any())).thenReturn(activeCooperation());
        when(contributionMapper.insert(any(PetCooperationContribution.class))).thenReturn(1);
        when(contributionMapper.selectCount(any())).thenReturn(0L);

        service.recordContribution(11L, "PLAY:1:2026-09-30");

        ArgumentCaptor<Long> locks = ArgumentCaptor.forClass(Long.class);
        verify(guardService, org.mockito.Mockito.atLeastOnce()).lockGuard(locks.capture());
        assertThat(locks.getAllValues()).containsExactly(11L, 22L);
    }

    @Test
    @DisplayName("PET-16：锁内重读队伍已消失（退出/流转）→ 不再写贡献")
    void contributionSkippedWhenTeamGoneAfterLocks() {
        // 第一次读（锁前）返回在队，第二次读（锁内）返回 null
        when(cooperationMapper.selectOne(any())).thenReturn(activeCooperation(), (PetCooperation) null);

        service.recordContribution(22L, "FEED:1:2026-09-30");

        verify(contributionMapper, never()).insert(any(PetCooperationContribution.class));
    }
}
