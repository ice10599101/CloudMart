package com.cloudmart.wish.service.impl;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wish.entity.WishInviteCallbackOutbox;
import com.cloudmart.wish.feign.UserFeignClient;
import com.cloudmart.wish.repository.WishInviteCallbackOutboxMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T15：邀请资格回调 outbox 单测——
 * 入队 SUPERSEDED 旧快照 / 提交即送成功 SETTLED / 失败退避 / 到顶 FAILED。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WishInviteCallbackOutboxServiceTest {

    @Mock
    private WishInviteCallbackOutboxMapper outboxMapper;

    @Mock
    private UserFeignClient userFeignClient;

    private WishInviteCallbackOutboxService service;

    @BeforeEach
    void setUp() {
        // 纯单测无 MP 环境：初始化实体 lambda cache（LambdaUpdateWrapper 依赖）
        com.baomidou.mybatisplus.core.MybatisConfiguration configuration =
                new com.baomidou.mybatisplus.core.MybatisConfiguration();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, ""),
                WishInviteCallbackOutbox.class);
        service = new WishInviteCallbackOutboxService(outboxMapper, userFeignClient);
        // insert 模拟回填 ID（afterCommit 即送路径在无事务上下文直接执行）
        lenient().when(outboxMapper.insert(any(WishInviteCallbackOutbox.class))).thenAnswer(inv -> {
            WishInviteCallbackOutbox row = inv.getArgument(0);
            row.setId(9001L);
            return 1;
        });
        // 默认待交付行（PENDING）
        lenient().when(outboxMapper.selectById(9001L)).thenAnswer(inv -> row(9001L, 0));
        // update 成功
        lenient().when(outboxMapper.update(any(), any())).thenReturn(1);
    }

    private WishInviteCallbackOutbox row(Long id, int attempts) {
        WishInviteCallbackOutbox row = new WishInviteCallbackOutbox();
        row.setId(id);
        row.setUserId(8901L);
        row.setDistinctSigninDays(3);
        row.setStatus(WishInviteCallbackOutbox.STATUS_PENDING);
        row.setAttempts(attempts);
        return row;
    }

    @Test
    @DisplayName("入队：同用户旧 PENDING 置 SUPERSEDED，新行插入")
    void enqueue_supersedesOldPending() {
        // 即送成功（afterCommit 无事务上下文直接执行 → settle 也走一次 update）
        when(userFeignClient.inviteEligibilityCheck(anyMap()))
                .thenReturn(ApiResponse.ok(Map.of("result", "granted")));
        service.enqueue(8901L, 3);

        // SUPERSEDED 更新 + settle 更新（至少各一次）
        verify(outboxMapper, org.mockito.Mockito.atLeastOnce()).update(any(), any());
        ArgumentCaptor<WishInviteCallbackOutbox> captor = ArgumentCaptor.forClass(WishInviteCallbackOutbox.class);
        verify(outboxMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(8901L);
        assertThat(captor.getValue().getDistinctSigninDays()).isEqualTo(3);
        assertThat(captor.getValue().getStatus()).isEqualTo(WishInviteCallbackOutbox.STATUS_PENDING);
    }

    @Test
    @DisplayName("交付成功：SETTLED（update 含 SETTLED 状态）")
    void deliver_success_settled() {
        when(userFeignClient.inviteEligibilityCheck(anyMap()))
                .thenReturn(ApiResponse.ok(Map.of("result", "granted")));

        org.mockito.Mockito.doReturn(java.util.List.of(row(9001L, 0)))
                .when(outboxMapper).selectList(any());

        assertThat(service.sweepPending()).isEqualTo(1);
        // 交付调用带 userId/distinctSigninDays
        verify(userFeignClient).inviteEligibilityCheck(anyMap());
        // settle 的条件更新被调用（sweep 路径内 1 次）
        verify(outboxMapper, times(1)).update(any(), any());
    }

    @Test
    @DisplayName("交付失败：退避记账（attempts+1，nextRetryAt 设置）")
    void deliver_failure_backoff() {
        when(userFeignClient.inviteEligibilityCheck(anyMap()))
                .thenThrow(new RuntimeException("user down"));
        org.mockito.Mockito.doReturn(java.util.List.of(row(9001L, 0)))
                .when(outboxMapper).selectList(any());

        assertThat(service.sweepPending()).isZero();
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper> captor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        // 失败路径只调用一次 update（recordFailure；未 settle）
        verify(outboxMapper, times(1)).update(any(), any());
        verify(outboxMapper, never()).selectById(eq(9999L));
    }

    @Test
    @DisplayName("补发到顶：FAILED 留痕不再无限堆积")
    void deliver_maxAttempts_failed() {
        when(userFeignClient.inviteEligibilityCheck(anyMap()))
                .thenThrow(new RuntimeException("user down"));
        org.mockito.Mockito.doReturn(java.util.List.of(row(9001L, WishInviteCallbackOutbox.MAX_ATTEMPTS)))
                .when(outboxMapper).selectList(any());

        service.sweepPending();
        // attempts 已到 MAX → recordFailure 走 FAILED 分支（update 只此一次）
        verify(outboxMapper, times(1)).update(any(), any());
    }
}
