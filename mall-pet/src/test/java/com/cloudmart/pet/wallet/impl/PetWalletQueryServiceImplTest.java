package com.cloudmart.pet.wallet.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.pet.entity.PetWalletTransaction;
import com.cloudmart.pet.repository.PetWalletAccountMapper;
import com.cloudmart.pet.repository.PetWalletTransactionMapper;
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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R09 钱包流水分页测试：size=50 时 hasMore 探测读必须真正取到第 51 行
 * （原实现服务端把探测读截断到 50，第 51 条永远不可见，T09）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PetWalletQueryServiceImpl 分页测试（R09）")
class PetWalletQueryServiceImplTest {

    @Mock
    private PetWalletAccountMapper accountMapper;
    @Mock
    private PetWalletTransactionMapper transactionMapper;

    private PetWalletQueryServiceImpl queryService;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PetWalletTransaction.class);
    }

    @BeforeEach
    void setUp() {
        queryService = new PetWalletQueryServiceImpl(accountMapper, transactionMapper,
                org.mockito.Mockito.mock(com.cloudmart.pet.wallet.PetWalletService.class));
    }

    @Test
    @DisplayName("size=50+1 探测读：LIMIT 允许 51 行，不再截断为 50")
    void probeReadAllowsLimitPlusOne() {
        queryService.listTransactions(100L, null, 51, null, null);

        ArgumentCaptor<LambdaQueryWrapper<PetWalletTransaction>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(transactionMapper).selectList(captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("LIMIT 51");
    }

    @Test
    @DisplayName("探测读上限仍受限：size 超过 51 也只取 51 行（公共页大小≤50 + 1 探测行）")
    void probeReadUpperBounded() {
        queryService.listTransactions(100L, null, 500, null, null);

        ArgumentCaptor<LambdaQueryWrapper<PetWalletTransaction>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(transactionMapper).selectList(captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("LIMIT 51");
    }

    @Test
    @DisplayName("size=50 探测读：51 行齐全时调用方可正确判定 hasMore")
    void hasMoreDetectableAtMaxPageSize() {
        List<PetWalletTransaction> rows = new ArrayList<>();
        for (long i = 0; i < 51; i++) {
            PetWalletTransaction tx = new PetWalletTransaction();
            tx.setId(1000L - i);
            tx.setUserId(100L);
            rows.add(tx);
        }
        when(transactionMapper.selectList(any())).thenReturn(rows);

        List<PetWalletTransaction> result = queryService.listTransactions(100L, null, 51, null, null);

        // 服务端返回 51 行：调用方 rows.size()>50 即 hasMore=true，第 51 条可达
        assertThat(result).hasSize(51);
    }
}
