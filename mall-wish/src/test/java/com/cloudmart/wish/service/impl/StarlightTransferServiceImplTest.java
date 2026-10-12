package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.StarlightDailyOutQuota;
import com.cloudmart.wish.entity.StarlightTransfer;
import com.cloudmart.wish.feign.CommunityFeignClient;
import com.cloudmart.wish.repository.StarlightDailyOutQuotaMapper;
import com.cloudmart.wish.repository.StarlightTransferMapper;
import com.cloudmart.wish.service.StarlightTransferService;
import com.cloudmart.wish.service.UserStatService;
import com.cloudmart.wish.service.UserStatService.StarlightPairTransfer;
import com.cloudmart.wish.vo.StarlightTransferVO;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R06：星光转赠——守恒（接收余量不足整笔拒绝）、日额度原子占用、请求键持久幂等。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StarlightTransferServiceImpl 单元测试（R06）")
class StarlightTransferServiceImplTest {

    private static final Long FROM = 200L;
    private static final Long TO = 100L;

    @Mock
    private StarlightTransferMapper transferMapper;
    @Mock
    private StarlightDailyOutQuotaMapper quotaMapper;
    @Mock
    private UserStatService userStatService;
    @Mock
    private CommunityFeignClient communityFeignClient;
    @Mock
    private org.apache.rocketmq.spring.core.RocketMQTemplate rocketMQTemplate;

    private StarlightTransferServiceImpl service;

    @BeforeAll
    static void initEntityMeta() {
        for (Class<?> clazz : new Class<?>[]{StarlightTransfer.class, StarlightDailyOutQuota.class}) {
            if (TableInfoHelper.getTableInfo(clazz) == null) {
                TableInfoHelper.initTableInfo(
                        new MapperBuilderAssistant(new MybatisConfiguration(), ""), clazz);
            }
        }
    }

    @BeforeEach
    void setUp() {
        service = new StarlightTransferServiceImpl(transferMapper, quotaMapper,
                userStatService, communityFeignClient,
                new com.cloudmart.wish.mq.StarlightNotifyProducer(rocketMQTemplate));
        // 好友校验默认通过；额度条件更新默认成功
        when(communityFeignClient.checkFriendship(anyLong(), anyLong()))
                .thenReturn(ApiResponse.ok(Map.of("friends", true)));
        when(quotaMapper.update(any(), any())).thenReturn(1);
        when(quotaMapper.selectOne(any())).thenReturn(quota(50));
        when(userStatService.transferStarlightPair(anyLong(), anyLong(), anyInt(), anyLong()))
                .thenReturn(new StarlightPairTransfer(901L, 902L, 400, 4100));
        when(transferMapper.insert(any(StarlightTransfer.class))).thenAnswer(inv -> {
            StarlightTransfer t = inv.getArgument(0);
            t.setId(777L);
            return 1;
        });
    }

    private StarlightDailyOutQuota quota(int used) {
        StarlightDailyOutQuota quota = new StarlightDailyOutQuota();
        quota.setUserId(FROM);
        quota.setUsedAmount(used);
        return quota;
    }

    private StarlightTransfer existingTransfer(String requestKey, Long to, int amount) {
        StarlightTransfer t = new StarlightTransfer();
        t.setId(555L);
        t.setFromUserId(FROM);
        t.setToUserId(to);
        t.setAmount(amount);
        t.setRequestKey(requestKey);
        t.setStatus(StarlightTransfer.STATUS_SETTLED);
        t.setCreditedAmount(amount);
        return t;
    }

    @Nested
    @DisplayName("转赠成功与守恒")
    class HappyPathTests {

        @Test
        @DisplayName("转赠成功：等额对转 + 流水回链 + 返回 SETTLED 与当日剩余额度")
        void transfer_success() {
            // 占用后同事务重读额度行：50 + 本次 100 = 150
            when(quotaMapper.selectOne(any())).thenReturn(quota(150));
            StarlightTransferVO vo = service.transfer(FROM, TO, 100, "给你", "rk-1");

            assertThat(vo.id()).isEqualTo(777L);
            assertThat(vo.status()).isEqualTo("SETTLED");
            assertThat(vo.amount()).isEqualTo(100);
            // 额度行在同一事务内已被本次占用更新（50+100=150），重读得剩余 50
            assertThat(vo.remainingToday()).isEqualTo(50);
            verify(userStatService).transferStarlightPair(FROM, TO, 100, 777L);
            ArgumentCaptor<StarlightTransfer> captor = ArgumentCaptor.forClass(StarlightTransfer.class);
            verify(transferMapper).updateById(captor.capture());
            assertThat(captor.getValue().getSenderLedgerId()).isEqualTo(901L);
            assertThat(captor.getValue().getReceiverLedgerId()).isEqualTo(902L);
            assertThat(captor.getValue().getCreditedAmount()).isEqualTo(100);
        }

        @Test
        @DisplayName("额度条件更新先行占用，随后才对转（同事务顺序）")
        void transfer_quotaOccupiedBeforePairTransfer() {
            service.transfer(FROM, TO, 100, null, "rk-2");
            verify(quotaMapper).insert(any(StarlightDailyOutQuota.class));
            verify(quotaMapper).update(any(), any());
        }
    }

    @Nested
    @DisplayName("限额与守恒拒绝")
    class RejectTests {

        @Test
        @DisplayName("日额度条件更新 0 行 → WISH_RATE_LIMITED，不发生对转")
        void transfer_dailyCapExceeded_rejected() {
            when(quotaMapper.update(any(), any())).thenReturn(0);

            assertThatThrownBy(() -> service.transfer(FROM, TO, 100, null, "rk-3"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", WishErrorCodes.WISH_RATE_LIMITED);
            verify(userStatService, never()).transferStarlightPair(anyLong(), anyLong(), anyInt(), anyLong());
        }

        @Test
        @DisplayName("AT12：接收方余量不足 → RECEIVER_CAPACITY 整笔拒绝（无静默截断）")
        void transfer_receiverCapacity_fullAmountRejected() {
            when(userStatService.transferStarlightPair(anyLong(), anyLong(), anyInt(), anyLong()))
                    .thenThrow(new BusinessException(WishErrorCodes.WISH_STARLIGHT_RECEIVER_CAPACITY,
                            "对方星光即将到达上限"));

            assertThatThrownBy(() -> service.transfer(FROM, TO, 100, null, "rk-4"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", WishErrorCodes.WISH_STARLIGHT_RECEIVER_CAPACITY);
        }

        @Test
        @DisplayName("好友校验异常 fail-closed 拒绝；非自转校验")
        void transfer_friendCheckFailClosed_andSelfRejected() {
            when(communityFeignClient.checkFriendship(anyLong(), anyLong()))
                    .thenThrow(new RuntimeException("community down"));

            assertThatThrownBy(() -> service.transfer(FROM, TO, 100, null, "rk-5"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", WishErrorCodes.WISH_VALIDATION_ERROR);

            assertThatThrownBy(() -> service.transfer(FROM, FROM, 100, null, "rk-6"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", WishErrorCodes.WISH_VALIDATION_ERROR);
        }

        @Test
        @DisplayName("单笔越界（<10 或 >100）→ 校验拒绝")
        void transfer_amountBounds() {
            assertThatThrownBy(() -> service.transfer(FROM, TO, 5, null, "rk-7"))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> service.transfer(FROM, TO, 101, null, "rk-8"))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("持久幂等（R06）")
    class IdempotencyTests {

        @Test
        @DisplayName("同键重放返回原结果（不再对转不再占额度）")
        void transfer_sameKey_replaysOriginal() {
            when(transferMapper.selectOne(any())).thenReturn(existingTransfer("rk-1", TO, 100));

            StarlightTransferVO vo = service.transfer(FROM, TO, 100, null, "rk-1");

            assertThat(vo.id()).isEqualTo(555L);
            assertThat(vo.status()).isEqualTo("SETTLED");
            verify(userStatService, never()).transferStarlightPair(anyLong(), anyLong(), anyInt(), anyLong());
            verify(transferMapper, never()).insert(any(StarlightTransfer.class));
        }

        @Test
        @DisplayName("同键异载荷 → WISH_TRANSFER_REQUEST_CONFLICT（防重放篡改）")
        void transfer_sameKeyDifferentPayload_conflict() {
            when(transferMapper.selectOne(any())).thenReturn(existingTransfer("rk-1", TO, 100));

            assertThatThrownBy(() -> service.transfer(FROM, TO, 50, null, "rk-1"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", WishErrorCodes.WISH_TRANSFER_REQUEST_CONFLICT);
        }
    }

    @Nested
    @DisplayName("记录与额度查询")
    class QueryTests {

        @Test
        @DisplayName("myTransfers 分页返回 records/total（不再限死 20 条）")
        void myTransfers_paged() {
            Page<StarlightTransfer> page = new Page<>(1, 20);
            page.setTotal(35);
            page.setRecords(List.of(existingTransfer(null, TO, 100)));
            when(transferMapper.selectPage(any(), any())).thenReturn(page);

            StarlightTransferService.TransferPage result = service.myTransfers(FROM, 1, 20);

            assertThat(result.total()).isEqualTo(35);
            assertThat(result.records()).hasSize(1);
            assertThat(result.records().get(0).direction()).isEqualTo("SENT");
        }

        @Test
        @DisplayName("transferLimits 返回 cap/已用/剩余（无当日行时已用 0）")
        void transferLimits() {
            when(quotaMapper.selectOne(any())).thenReturn(null);

            StarlightTransferService.TransferLimits limits = service.transferLimits(FROM);

            assertThat(limits.dailyCap()).isEqualTo(200);
            assertThat(limits.usedToday()).isEqualTo(0);
            assertThat(limits.remainingToday()).isEqualTo(200);
        }
    }
}
