package com.cloudmart.user.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.user.entity.UserRewardObligation;
import com.cloudmart.user.feign.WishStarlightFeignClient;
import com.cloudmart.user.repository.UserRewardObligationMapper;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * R07：邀请奖励交付义务——幂等入队、成功置 SETTLED、失败退避（AT14 断点恢复语义）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UserRewardObligationService 单元测试（R07）")
class UserRewardObligationServiceTest {

    @Mock
    private UserRewardObligationMapper obligationMapper;
    @Mock
    private WishStarlightFeignClient wishStarlightFeignClient;

    private UserRewardObligationService service;

    @BeforeAll
    static void initEntityMeta() {
        if (TableInfoHelper.getTableInfo(UserRewardObligation.class) == null) {
            TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserRewardObligation.class);
        }
    }

    @BeforeEach
    void setUp() {
        service = new UserRewardObligationService(obligationMapper, wishStarlightFeignClient);
        when(obligationMapper.selectById(any())).thenAnswer(inv -> {
            UserRewardObligation o = new UserRewardObligation();
            o.setId(inv.getArgument(0, Long.class));
            o.setBizKey("INVITE_REWARD:1:8");
            o.setSource("INVITE_REWARD");
            o.setSourceBizId("1");
            o.setBeneficiary(8L);
            o.setRewardType(UserRewardObligation.TYPE_STARLIGHT);
            o.setAmount(50);
            o.setStatus(UserRewardObligation.STATUS_PENDING);
            o.setAttempts(0);
            return o;
        });
    }

    @Test
    @DisplayName("补发成功 → SETTLED（远端 earn 幂等键防重）")
    void sweep_deliversDue() {
        when(obligationMapper.selectList(any())).thenReturn(List.of(pending(1L)));
        when(wishStarlightFeignClient.earn(any(), any(), any(), any(), any()))
                .thenReturn(ApiResponse.ok(Map.of("credited", 50)));

        assertThat(service.sweepPending()).isEqualTo(1);
    }

    @Test
    @DisplayName("远端失败 → 退避记账不外抛，不误置终态")
    void sweep_failure_backsOff() {
        when(obligationMapper.selectList(any())).thenReturn(List.of(pending(2L)));
        when(wishStarlightFeignClient.earn(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("wish down"));

        assertThat(service.sweepPending()).isZero();
    }

    private UserRewardObligation pending(long id) {
        UserRewardObligation o = new UserRewardObligation();
        o.setId(id);
        o.setBizKey("INVITE_REWARD:" + id + ":8");
        o.setSource("INVITE_REWARD");
        o.setSourceBizId(String.valueOf(id));
        o.setBeneficiary(8L);
        o.setRewardType(UserRewardObligation.TYPE_STARLIGHT);
        o.setAmount(50);
        o.setStatus(UserRewardObligation.STATUS_PENDING);
        o.setAttempts(0);
        return o;
    }
}
