package com.cloudmart.pet.wallet.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.pet.config.PetMetrics;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.entity.PetWalletAccount;
import com.cloudmart.pet.entity.PetWalletReconcileRun;
import com.cloudmart.pet.mq.PetEventProducer;
import com.cloudmart.pet.repository.PetWalletAccountMapper;
import com.cloudmart.pet.repository.PetWalletReconcileItemMapper;
import com.cloudmart.pet.repository.PetWalletReconcileRunMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R19 对账覆盖与恢复测试：keyset 分批扫到本轮上界（第 5001+ 账户可达）、
 * 游标随批持久化（中断续跑不重扫）、SQL SUM 聚合（内存有界）、差异逐条计数。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PetWalletReconcileJob 覆盖与恢复测试（R19）")
class PetWalletReconcileJobTest {

    @Mock
    private PetWalletAccountMapper accountMapper;
    @Mock
    private PetWalletReconcileRunMapper runMapper;
    @Mock
    private PetWalletReconcileItemMapper itemMapper;
    @Mock
    private PetMetrics metrics;
    @Mock
    private PetEventProducer eventProducer;
    @Mock
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private PetWalletReconcileJob job;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PetWalletReconcileRun.class);
    }

    @BeforeEach
    void setUp() {
        PetProperties properties = new PetProperties();
        job = new PetWalletReconcileJob(accountMapper, runMapper, itemMapper, metrics,
                eventProducer, properties, jdbcTemplate);
        lenient().when(runMapper.insert(any(PetWalletReconcileRun.class))).thenReturn(1);
        lenient().when(runMapper.updateById(any(PetWalletReconcileRun.class))).thenReturn(1);
        lenient().when(itemMapper.insert(any(com.cloudmart.pet.entity.PetWalletReconcileItem.class))).thenReturn(1);
        lenient().when(jdbcTemplate.queryForObject(contains("MAX(id)"), eq(Long.class)))
                .thenReturn(3L);
    }

    private PetWalletAccount account(long id, long balance, long version, long realSum) {
        PetWalletAccount account = new PetWalletAccount();
        account.setId(id);
        account.setUserId(id * 10);
        account.setBalance(balance);
        account.setVersion(version);
        lenient().when(jdbcTemplate.queryForObject(
                        contains("SUM(delta)"), eq(Long.class), eq(id), eq(version)))
                .thenReturn(realSum);
        return account;
    }

    @Test
    @DisplayName("R19：keyset 分批扫到上界——多批账户全部覆盖并 COMPLETED")
    void keysetCoversAllBatches() {
        PetWalletAccount a1 = account(1, 100, 5, 100);
        PetWalletAccount a2 = account(2, 50, 3, 50);
        PetWalletAccount a3 = account(3, 80, 2, 80);
        when(accountMapper.selectList(any()))
                .thenReturn(List.of(a1, a2))
                .thenReturn(List.of(a3))
                .thenReturn(List.of());

        job.reconcile("TEST");

        ArgumentCaptor<PetWalletReconcileRun> captor =
                ArgumentCaptor.forClass(PetWalletReconcileRun.class);
        verify(runMapper, org.mockito.Mockito.atLeastOnce()).updateById(captor.capture());
        PetWalletReconcileRun last = captor.getValue();
        assertThat(last.getStatus()).isEqualTo("COMPLETED");
        assertThat(last.getAccountCount()).isEqualTo(3);
        // 游标推进到上界
        assertThat(last.getCursorAccountId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("R19：账本不平 → 差异项 OPEN 落库并按条数计指标")
    void diffRecordedAndCounted() {
        PetWalletAccount bad = account(1, 100, 5, 90);
        when(accountMapper.selectList(any()))
                .thenReturn(List.of(bad))
                .thenReturn(List.of());

        job.reconcile("TEST");

        verify(itemMapper).insert(any(com.cloudmart.pet.entity.PetWalletReconcileItem.class));
        // 逐条差异计数（原实现每轮固定 +1）
        verify(metrics, times(1)).increment(eq("pet_wallet_reconcile_diff"), anyString(), anyString());
    }

    @Test
    @DisplayName("R19：SUM 聚合以账户版本为上界（防并发新单假差异）")
    void sumBoundedByVersion() {
        PetWalletAccount a = account(1, 100, 7, 100);
        when(accountMapper.selectList(any()))
                .thenReturn(List.of(a))
                .thenReturn(List.of());

        job.reconcile("TEST");

        verify(jdbcTemplate).queryForObject(contains("account_version <= ?"),
                eq(Long.class), eq(1L), eq(7L));
    }

    @Test
    @DisplayName("R19：FAILED run 游标续跑——只扫游标之后账户，不重扫")
    void resumeFromCursor() {
        PetWalletReconcileRun failed = new PetWalletReconcileRun();
        failed.setId(9L);
        failed.setStatus("FAILED");
        failed.setMaxAccountId(3L);
        failed.setCursorAccountId(2L);
        failed.setAccountCount(2);
        failed.setDiffCount(0);
        when(runMapper.selectOne(any())).thenReturn(failed);
        PetWalletAccount a3 = account(3, 80, 2, 80);
        when(accountMapper.selectList(any()))
                .thenReturn(List.of(a3))
                .thenReturn(List.of());

        job.reconcile("TEST");

        // 只插入 id>2 的账户批（keyset 条件由 wrapper 携带，mock 层验证扫描次数为 1 批）
        verify(accountMapper, times(1)).selectList(any());
        ArgumentCaptor<PetWalletReconcileRun> captor =
                ArgumentCaptor.forClass(PetWalletReconcileRun.class);
        verify(runMapper, org.mockito.Mockito.atLeastOnce()).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("COMPLETED");
        assertThat(captor.getValue().getCursorAccountId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("R19：全部平账不发送告警")
    void noAlertWhenBalanced() {
        PetWalletAccount a = account(1, 100, 5, 100);
        when(accountMapper.selectList(any()))
                .thenReturn(List.of(a))
                .thenReturn(List.of());

        job.reconcile("TEST");

        verify(eventProducer, never()).publishViaOutbox(anyString(), any(), any());
    }
}
