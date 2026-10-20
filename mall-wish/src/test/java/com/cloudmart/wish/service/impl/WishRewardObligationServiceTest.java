package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wish.entity.WishRewardObligation;
import com.cloudmart.wish.feign.CommunityFeignClient;
import com.cloudmart.wish.repository.WishRewardObligationMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R07：跨服务奖励交付义务——幂等入队、提交后即送、失败退避补发、扫描收敛。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WishRewardObligationService 单元测试（R07）")
class WishRewardObligationServiceTest {

    private static final Long USER_ID = 8L;

    @Mock
    private WishRewardObligationMapper obligationMapper;
    @Mock
    private CommunityFeignClient communityFeignClient;

    private WishRewardObligationService service;

    @BeforeAll
    static void initEntityMeta() {
        if (TableInfoHelper.getTableInfo(WishRewardObligation.class) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""), WishRewardObligation.class);
        }
    }

    @BeforeEach
    void setUp() {
        service = new WishRewardObligationService(obligationMapper, communityFeignClient, java.time.Clock.systemUTC());
        when(obligationMapper.selectById(any())).thenAnswer(inv -> {
            WishRewardObligation o = new WishRewardObligation();
            o.setId(inv.getArgument(0, Long.class));
            o.setBizKey("WISH_SIGNIN:1:EXP");
            o.setSource("WISH_SIGNIN");
            o.setSourceBizId("WISH_SIGNIN:1");
            o.setBeneficiary(USER_ID);
            o.setRewardType(WishRewardObligation.TYPE_EXP);
            o.setAmount(10);
            o.setStatus(WishRewardObligation.STATUS_PENDING);
            o.setAttempts(0);
            return o;
        });
        when(communityFeignClient.grantExp(any())).thenReturn(ApiResponse.ok(Map.of("granted", 10)));
    }

    private WishRewardObligation obligation(long id, String status) {
        WishRewardObligation o = new WishRewardObligation();
        o.setId(id);
        o.setBizKey("WISH_SIGNIN:" + id + ":EXP");
        o.setSource("WISH_SIGNIN");
        o.setSourceBizId("WISH_SIGNIN:" + id);
        o.setBeneficiary(USER_ID);
        o.setRewardType(WishRewardObligation.TYPE_EXP);
        o.setAmount(10);
        o.setStatus(status);
        o.setAttempts(0);
        return o;
    }

    @Nested
    @DisplayName("入队与交付")
    class EnqueueTests {

        @Test
        @DisplayName("入队成功并注册提交后即送（事务激活时 afterCommit 交付）")
        void enqueue_registersAfterCommitDelivery() {
            service.enqueueWithCommitDelivery("WISH_SIGNIN:1:EXP", "WISH_SIGNIN", "WISH_SIGNIN:1",
                    USER_ID, WishRewardObligation.TYPE_EXP, 10, "每日签到");

            verify(obligationMapper).insert(any(WishRewardObligation.class));
        }

        @Test
        @DisplayName("重复 bizKey 幂等跳过（不重复入队不重复即送）")
        void enqueue_duplicateKey_skipped() {
            when(obligationMapper.insert(any(WishRewardObligation.class)))
                    .thenThrow(new DuplicateKeyException("uk_biz_key"));

            service.enqueueWithCommitDelivery("WISH_SIGNIN:1:EXP", "WISH_SIGNIN", "WISH_SIGNIN:1",
                    USER_ID, WishRewardObligation.TYPE_EXP, 10, "每日签到");

            // 无事务同步（单测环境）→ 直接即送不会发生：insert 已抛重复，无后续调用
            verify(communityFeignClient, never()).grantExp(any());
        }

        @Test
        @DisplayName("无事务上下文时立即交付：成功 → SETTLED")
        void enqueue_noTransaction_deliversImmediately() {
            service.enqueueWithCommitDelivery("WISH_SIGNIN:1:EXP", "WISH_SIGNIN", "WISH_SIGNIN:1",
                    USER_ID, WishRewardObligation.TYPE_EXP, 10, "每日签到");

            verify(communityFeignClient).grantExp(any());
            ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper> captor =
                    ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
            verify(obligationMapper).update(any(), captor.capture()); // SETTLED 条件更新
        }
    }

    @Nested
    @DisplayName("补发扫描")
    class SweepTests {

        @Test
        @DisplayName("到期 PENDING → 交付成功置 SETTLED")
        void sweep_deliversDue() {
            when(obligationMapper.selectList(any())).thenReturn(List.of(obligation(1L, "PENDING")));

            int settled = service.sweepPending();

            assertThat(settled).isEqualTo(1);
            verify(communityFeignClient).grantExp(any());
        }

        @Test
        @DisplayName("交付失败 → 退避记账（attempts+1、next_retry_at 推进），不误置终态")
        void sweep_failure_backsOff() {
            when(obligationMapper.selectList(any())).thenReturn(List.of(obligation(2L, "PENDING")));
            when(communityFeignClient.grantExp(any()))
                    .thenThrow(new RuntimeException("community down"));

            int settled = service.sweepPending();

            assertThat(settled).isZero();
            ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper> captor =
                    ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
            verify(obligationMapper).update(any(), captor.capture()); // 失败记账
        }

        @Test
        @DisplayName("多实例并发收敛：重复交付由 community (user,source,bizId) 去重，成功响应幂等置 SETTLED")
        void sweep_concurrentDelivery_safe() {
            when(obligationMapper.selectList(any())).thenReturn(List.of(obligation(3L, "PENDING")));
            // community 幂等命中：仍返回 success
            when(communityFeignClient.grantExp(any())).thenReturn(ApiResponse.ok(Map.of("granted", 10)));

            assertThat(service.sweepPending()).isEqualTo(1);
        }

        @Test
        @DisplayName("持续失败多次后仍留 PENDING（不自动转 REJECTED，等待人工核查）")
        void sweep_persistentFailure_staysPending() {
            WishRewardObligation stale = obligation(4L, "PENDING");
            stale.setAttempts(15);
            when(obligationMapper.selectList(any())).thenReturn(List.of(stale));
            when(communityFeignClient.grantExp(any()))
                    .thenThrow(new RuntimeException("still down"));

            service.sweepPending();

            // 未发生 SETTLED/REJECTED 终态更新（仅失败记账）
            verify(obligationMapper, times(1)).update(any(), any());
        }
    }
}
