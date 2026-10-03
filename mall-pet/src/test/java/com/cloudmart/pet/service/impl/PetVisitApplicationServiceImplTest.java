package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.pet.config.PetClock;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.entity.PetVisitFact;
import com.cloudmart.pet.repository.PetVisitFactMapper;
import com.cloudmart.pet.service.PetVisitApplicationService.VisitGrant;
import com.cloudmart.pet.service.PetVisitApplicationService.VisitSource;
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
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B01/BE-06 拜访统一服务测试：事实唯一键冷却、收益额度门控、好友双额度回退。
 * 行锁语义（FOR UPDATE 串行化）需真实 MySQL（集成测试 NOT RUN，属 Q-01 门禁）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PetVisitApplicationServiceImpl 拜访统一事实")
class PetVisitApplicationServiceImplTest {

    @Mock
    private PetUserGuardServiceImpl guardService;
    @Mock
    private PetVisitFactMapper factMapper;
    @Mock
    private PetQuotaService quotaService;

    private PetVisitApplicationServiceImpl service;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PetVisitFact.class);
    }

    @BeforeEach
    void setUp() {
        PetProperties properties = new PetProperties();
        PetClock clock = new PetClock(java.time.Clock.systemUTC(), properties);
        service = new PetVisitApplicationServiceImpl(guardService, factMapper, quotaService, clock, properties);
        lenient().when(guardService.lockGuard(any())).thenReturn(new com.cloudmart.pet.entity.PetUserGuard());
        lenient().when(factMapper.insert(any(PetVisitFact.class))).thenReturn(1);
        lenient().when(factMapper.updateById(any(PetVisitFact.class))).thenReturn(1);
    }

    @Test
    @DisplayName("首次拜访且额度充足：事实落库 + 有收益")
    void firstVisit_withQuota_rewarded() {
        when(quotaService.tryConsume(any(), any(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);

        VisitGrant grant = service.recordVisit(100L, 200L, 1L, 2L, VisitSource.NEIGHBOR);

        assertThat(grant.factCreated()).isTrue();
        assertThat(grant.rewardGranted()).isTrue();
        verify(guardService).lockGuard(100L);
        ArgumentCaptorAssert(factMapper, 1);
    }

    @Test
    @DisplayName("同主人同业务日重复拜访：唯一键冲突 → 冷却拒绝且不占额度")
    void duplicateVisit_noQuotaConsumed() {
        when(factMapper.insert(any(PetVisitFact.class))).thenThrow(new DuplicateKeyException("uk"));

        VisitGrant grant = service.recordVisit(100L, 200L, 1L, 2L, VisitSource.NEIGHBOR);

        assertThat(grant.factCreated()).isFalse();
        assertThat(grant.rewardGranted()).isFalse();
        verify(quotaService, never()).tryConsume(any(), any(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("收益额度用尽：拜访事实仍成立但无收益（reward_granted=0 回写）")
    void overQuota_factWithoutReward() {
        when(quotaService.tryConsume(any(), any(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(false);

        VisitGrant grant = service.recordVisit(100L, 200L, 1L, 2L, VisitSource.NEIGHBOR);

        assertThat(grant.factCreated()).isTrue();
        assertThat(grant.rewardGranted()).isFalse();
        // 落库事实 reward_granted=0（无收益拜访成立，下游不推进）
        org.mockito.ArgumentCaptor<PetVisitFact> captor = org.mockito.ArgumentCaptor.forClass(PetVisitFact.class);
        org.mockito.Mockito.verify(factMapper).insert(captor.capture());
        assertThat(captor.getValue().getRewardGranted()).isEqualTo(0);
        assertThat(captor.getValue().getBusinessDate()).isEqualTo(petClockBusinessDate());
    }

    @Test
    @DisplayName("好友互访：友好额度先占后总额度失败 → 回退友好额度（不虚耗）")
    void friendVisit_dualQuotaRollback() {
        when(quotaService.tryConsume(any(), org.mockito.ArgumentMatchers.eq(PetQuotaService.QuotaType.FRIEND_VISIT_REWARD),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);
        when(quotaService.tryConsume(any(), org.mockito.ArgumentMatchers.eq(PetQuotaService.QuotaType.VISIT_REWARD),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(false);

        VisitGrant grant = service.recordVisit(100L, 200L, 1L, 2L, VisitSource.FRIEND);

        assertThat(grant.factCreated()).isTrue();
        assertThat(grant.rewardGranted()).isFalse();
        verify(quotaService).release(100L, PetQuotaService.QuotaType.FRIEND_VISIT_REWARD, 0);
    }

    @Test
    @DisplayName("R14：友好额度已满（本次未占用）→ 不释放任何额度（原实现误把历史 used 减 1）")
    void friendVisitQuotaFull_noRelease() {
        when(quotaService.tryConsume(any(), org.mockito.ArgumentMatchers.eq(PetQuotaService.QuotaType.FRIEND_VISIT_REWARD),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(false);

        VisitGrant grant = service.recordVisit(100L, 200L, 1L, 2L, VisitSource.FRIEND);

        assertThat(grant.factCreated()).isTrue();
        assertThat(grant.rewardGranted()).isFalse();
        // 友好额度失败短路：共享额度未尝试；两者都不释放（无占用即无回退）
        verify(quotaService, never()).tryConsume(any(), org.mockito.ArgumentMatchers.eq(PetQuotaService.QuotaType.VISIT_REWARD),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt());
        verify(quotaService, never()).release(any(), any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("R14：友好额度成功+共享额度成功 → 无任何释放")
    void friendVisitBothConsumed_noRelease() {
        when(quotaService.tryConsume(any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);

        VisitGrant grant = service.recordVisit(100L, 200L, 1L, 2L, VisitSource.FRIEND);

        assertThat(grant.rewardGranted()).isTrue();
        verify(quotaService, never()).release(any(), any(), org.mockito.ArgumentMatchers.anyLong());
    }

    private java.time.LocalDate petClockBusinessDate() {
        // 测试未固定时钟，直接以 PetClock 的上海口径重算当日业务日
        return java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
    }

    private static void ArgumentCaptorAssert(PetVisitFactMapper mapper, int times) {
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.times(times)).insert(any(PetVisitFact.class));
    }
}
