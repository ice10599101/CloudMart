package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.config.PetClock;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.dto.ExchangeCodeFactDTO;
import com.cloudmart.pet.entity.PetFeedEntitlement;
import com.cloudmart.pet.entity.PetLinkedProductOperation;
import com.cloudmart.pet.feign.CouponFeignClient;
import com.cloudmart.pet.repository.PetFeedEntitlementMapper;
import com.cloudmart.pet.repository.PetLinkedProductOperationMapper;
import com.cloudmart.pet.service.PetLinkedProductService.OperationView;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R01/R02：兑换码权威核验（拒绝随机码/他人码/未兑换/作废；PENDING 可恢复；同码同键幂等）
 * 与单份权益 CAS 占用（重放不二次消耗；占用成功才应用双倍）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PetLinkedProductServiceImpl 单元测试（R01/R02）")
class PetLinkedProductServiceImplTest {

    private static final Long USER_ID = 8L;

    @Mock
    private PetFeedEntitlementMapper entitlementMapper;
    @Mock
    private PetLinkedProductOperationMapper operationMapper;
    @Mock
    private CouponFeignClient couponFeignClient;

    private PetProperties properties;
    private PetClock petClock;
    private PetLinkedProductServiceImpl service;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        for (Class<?> clazz : new Class<?>[]{PetFeedEntitlement.class, PetLinkedProductOperation.class}) {
            if (TableInfoHelper.getTableInfo(clazz) == null) {
                MybatisConfiguration configuration = new MybatisConfiguration();
                MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
                assistant.setCurrentNamespace("com.cloudmart.pet.repository." + clazz.getSimpleName() + "Mapper");
                TableInfoHelper.initTableInfo(assistant, clazz);
            }
        }
    }

    @BeforeEach
    void setUp() {
        properties = new PetProperties();
        petClock = new PetClock(Clock.fixed(Instant.parse("2026-10-11T00:00:00Z"), ZoneOffset.UTC), properties);
        service = new PetLinkedProductServiceImpl(entitlementMapper, operationMapper,
                couponFeignClient, properties, petClock);
    }

    private ExchangeCodeFactDTO fact(Long codeId, String status, Long userId, Long templateId) {
        return new ExchangeCodeFactDTO(codeId, "CODE-1", status, userId,
                java.time.LocalDateTime.now(ZoneOffset.UTC), templateId);
    }

    private PetLinkedProductOperation persistedOperation(String status) {
        PetLinkedProductOperation op = new PetLinkedProductOperation();
        op.setId(1L);
        op.setOperationId("op-1");
        op.setRequestKey("rk-1");
        op.setUserId(USER_ID);
        op.setRedemptionCode("CODE-1");
        op.setStatus(status);
        op.setVersion(0);
        return op;
    }

    @Nested
    @DisplayName("R01：兑换码权威核验")
    class RedeemTests {

        @Test
        @DisplayName("本人已兑换码 → DELIVERED，权益发放（一份一行），操作台账回填事实")
        void redeem_happyPath_delivers() {
            when(operationMapper.selectOne(any())).thenReturn(null, null, null);
            when(entitlementMapper.selectOne(any())).thenReturn(null);
            when(couponFeignClient.getExchangeCodeFact("CODE-1"))
                    .thenReturn(ApiResponse.ok(fact(901L, "EXCHANGED", USER_ID, 555L)));
            when(entitlementMapper.insert(any(PetFeedEntitlement.class))).thenAnswer(inv -> {
                PetFeedEntitlement e = inv.getArgument(0);
                e.setId(71L);
                return 1;
            });

            OperationView view = service.redeem(USER_ID, "code-1", "rk-1");

            assertThat(view.status()).isEqualTo("DELIVERED");
            assertThat(view.entitlementId()).isEqualTo(71L);
            assertThat(view.usable()).isTrue();
            ArgumentCaptor<PetFeedEntitlement> entitlementCaptor =
                    ArgumentCaptor.forClass(PetFeedEntitlement.class);
            verify(entitlementMapper).insert(entitlementCaptor.capture());
            // 一份权益一行：unitSequence=1、来源事实回链、状态 AVAILABLE
            assertThat(entitlementCaptor.getValue().getSourceFactId()).isEqualTo(901L);
            assertThat(entitlementCaptor.getValue().getUnitSequence()).isEqualTo(1);
            assertThat(entitlementCaptor.getValue().getStatus())
                    .isEqualTo(PetFeedEntitlement.STATUS_AVAILABLE);
            // itemCode 服务端推导（模板未配置 → ANY，客户端不可指定）
            assertThat(entitlementCaptor.getValue().getItemCode()).isEqualTo("ANY");
        }

        @Test
        @DisplayName("模板→食物配置存在时 itemCode 由服务端配置推导")
        void redeem_templateMappedItemCode() {
            properties.getLinkedProduct().getTemplateItems().put("555", "snack-apple");
            when(operationMapper.selectOne(any())).thenReturn(null, null, null);
            when(entitlementMapper.selectOne(any())).thenReturn(null);
            when(couponFeignClient.getExchangeCodeFact("CODE-1"))
                    .thenReturn(ApiResponse.ok(fact(901L, "EXCHANGED", USER_ID, 555L)));
            when(entitlementMapper.insert(any(PetFeedEntitlement.class))).thenAnswer(inv -> {
                PetFeedEntitlement e = inv.getArgument(0);
                e.setId(72L);
                return 1;
            });

            service.redeem(USER_ID, "CODE-1", "rk-1");

            ArgumentCaptor<PetFeedEntitlement> captor = ArgumentCaptor.forClass(PetFeedEntitlement.class);
            verify(entitlementMapper).insert(captor.capture());
            assertThat(captor.getValue().getItemCode()).isEqualTo("snack-apple");
        }

        @Test
        @DisplayName("随机码（coupon 权威侧不存在）→ REJECTED PET_CODE_NOT_FOUND，不发放权益")
        void redeem_unknownCode_rejected() {
            when(operationMapper.selectOne(any())).thenReturn(null, null, null);
            when(entitlementMapper.selectOne(any())).thenReturn(null);
            when(couponFeignClient.getExchangeCodeFact("GHOST"))
                    .thenThrow(new BusinessException("EXCHANGE_CODE_NOT_FOUND", "兑换码不存在"));

            OperationView view = service.redeem(USER_ID, "GHOST", "rk-2");

            assertThat(view.status()).isEqualTo("REJECTED");
            assertThat(view.rejectReasonCode()).isEqualTo(PetErrorCodes.PET_CODE_NOT_FOUND);
            verify(entitlementMapper, never()).insert(any(PetFeedEntitlement.class));
        }

        @Test
        @DisplayName("他人兑换码 → REJECTED PET_CODE_NOT_OWNED")
        void redeem_otherUsersCode_rejected() {
            when(operationMapper.selectOne(any())).thenReturn(null, null, null);
            when(entitlementMapper.selectOne(any())).thenReturn(null);
            when(couponFeignClient.getExchangeCodeFact("CODE-1"))
                    .thenReturn(ApiResponse.ok(fact(901L, "EXCHANGED", 999L, 1L)));

            OperationView view = service.redeem(USER_ID, "CODE-1", "rk-3");

            assertThat(view.status()).isEqualTo("REJECTED");
            assertThat(view.rejectReasonCode()).isEqualTo(PetErrorCodes.PET_CODE_NOT_OWNED);
            verify(entitlementMapper, never()).insert(any(PetFeedEntitlement.class));
        }

        @Test
        @DisplayName("未兑换/已作废码 → REJECTED，前提不成立")
        void redeem_notExchangedOrDisabled_rejected() {
            when(operationMapper.selectOne(any())).thenReturn(null, null, null, null, null, null);
            when(entitlementMapper.selectOne(any())).thenReturn(null);
            when(couponFeignClient.getExchangeCodeFact("CODE-1")).thenReturn(
                    ApiResponse.ok(fact(901L, "UNUSED", USER_ID, 1L)),
                    ApiResponse.ok(fact(901L, "DISABLED", USER_ID, 1L)));

            OperationView unused = service.redeem(USER_ID, "CODE-1", "rk-4");
            assertThat(unused.status()).isEqualTo("REJECTED");
            assertThat(unused.rejectReasonCode()).isEqualTo(PetErrorCodes.PET_CODE_NOT_EXCHANGED);

            OperationView disabled = service.redeem(USER_ID, "CODE-1", "rk-5");
            assertThat(disabled.status()).isEqualTo("REJECTED");
            assertThat(disabled.rejectReasonCode()).isEqualTo(PetErrorCodes.PET_CODE_DISABLED);
            verify(entitlementMapper, never()).insert(any(PetFeedEntitlement.class));
        }

        @Test
        @DisplayName("coupon 服务不可用 → PENDING（可恢复），lastError 落台账，不发放不拒绝")
        void redeem_couponUnavailable_pending() {
            when(operationMapper.selectOne(any())).thenReturn(null, null, null);
            when(entitlementMapper.selectOne(any())).thenReturn(null);
            when(couponFeignClient.getExchangeCodeFact("CODE-1"))
                    .thenThrow(new BusinessException("COUPON_SERVICE_UNAVAILABLE", "优惠券服务暂不可用，核验稍后重试"));

            OperationView view = service.redeem(USER_ID, "CODE-1", "rk-6");

            assertThat(view.status()).isEqualTo("PENDING");
            assertThat(view.usable()).isFalse();
            ArgumentCaptor<PetLinkedProductOperation> opCaptor =
                    ArgumentCaptor.forClass(PetLinkedProductOperation.class);
            verify(operationMapper).updateById(opCaptor.capture());
            assertThat(opCaptor.getValue().getStatus()).isEqualTo("PENDING");
            assertThat(opCaptor.getValue().getLastError()).contains("暂不可用");
            verify(entitlementMapper, never()).insert(any(PetFeedEntitlement.class));
        }

        @Test
        @DisplayName("同 requestKey 重放返回原操作（不再核验不再发放）")
        void redeem_sameRequestKey_replaysOriginalOperation() {
            PetLinkedProductOperation original = persistedOperation("DELIVERED");
            original.setEntitlementId(71L);
            when(operationMapper.selectOne(any())).thenReturn(original);

            OperationView view = service.redeem(USER_ID, "CODE-1", "rk-1");

            assertThat(view.operationId()).isEqualTo("op-1");
            assertThat(view.status()).isEqualTo("DELIVERED");
            verify(couponFeignClient, never()).getExchangeCodeFact(anyString());
            verify(entitlementMapper, never()).insert(any(PetFeedEntitlement.class));
        }

        @Test
        @DisplayName("同码已发放（重复提交不同键）→ 返回 DELIVERED 原结果，同码只发一次")
        void redeem_sameCodeAlreadyGranted_returnsDelivered() {
            PetFeedEntitlement granted = new PetFeedEntitlement();
            granted.setId(71L);
            granted.setUserId(USER_ID);
            granted.setRedemptionCode("CODE-1");
            granted.setSourceFactId(901L);
            granted.setStatus(PetFeedEntitlement.STATUS_AVAILABLE);
            when(operationMapper.selectOne(any())).thenReturn(null, null);
            when(entitlementMapper.selectOne(any())).thenReturn(granted, null);

            OperationView view = service.redeem(USER_ID, "CODE-1", "rk-other");

            assertThat(view.status()).isEqualTo("DELIVERED");
            assertThat(view.entitlementId()).isEqualTo(71L);
            verify(couponFeignClient, never()).getExchangeCodeFact(anyString());
        }

        @Test
        @DisplayName("PENDING 操作按 operationId 重查时原单重试核验（恢复路径）")
        void getOperation_pendingRetriesVerification() {
            PetLinkedProductOperation pending = persistedOperation("PENDING");
            pending.setLastError("优惠券服务暂不可用");
            when(operationMapper.selectOne(any())).thenReturn(pending);
            when(couponFeignClient.getExchangeCodeFact("CODE-1"))
                    .thenReturn(ApiResponse.ok(fact(901L, "EXCHANGED", USER_ID, 1L)));
            when(entitlementMapper.insert(any(PetFeedEntitlement.class))).thenAnswer(inv -> {
                PetFeedEntitlement e = inv.getArgument(0);
                e.setId(73L);
                return 1;
            });

            OperationView view = service.getOperation(USER_ID, "op-1");

            assertThat(view.status()).isEqualTo("DELIVERED");
            assertThat(view.entitlementId()).isEqualTo(73L);
        }

        @Test
        @DisplayName("他人操作按 ID 查询 → PET_NOT_FOUND（不泄露存在性）")
        void getOperation_ofOtherUser_notFound() {
            PetLinkedProductOperation other = persistedOperation("DELIVERED");
            other.setUserId(999L);
            when(operationMapper.selectOne(any())).thenReturn(other);

            org.assertj.core.api.Assertions.assertThatThrownBy(
                            () -> service.getOperation(USER_ID, "op-1"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", PetErrorCodes.PET_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("R02：单份权益 CAS 占用")
    class ConsumeTests {

        private PetFeedEntitlement entitlement(long id, String status) {
            PetFeedEntitlement e = new PetFeedEntitlement();
            e.setId(id);
            e.setUserId(USER_ID);
            e.setRedemptionCode("C" + id);
            e.setStatus(status);
            return e;
        }

        @Test
        @DisplayName("占用成功返回被占用权益 ID，状态置 CONSUMED 并绑定喂食操作键")
        void consume_success_returnsEntitlementId() {
            when(entitlementMapper.selectOne(any()))
                    .thenReturn((PetFeedEntitlement) null)   // 重放查询：无
                    .thenReturn(entitlement(71L, PetFeedEntitlement.STATUS_AVAILABLE));
            when(entitlementMapper.update(any(), any())).thenReturn(1);

            Optional<Long> consumed = service.consumeOneForFeed(USER_ID, 5L, "feed-op-1");

            assertThat(consumed).contains(71L);
            verify(entitlementMapper).update(any(), any());
        }

        @Test
        @DisplayName("同一 feedOperationId 重放 → 返回原占用，不再消耗第二份")
        void consume_sameFeedOperationId_replaysWithoutSecondConsume() {
            PetFeedEntitlement replayed = entitlement(71L, PetFeedEntitlement.STATUS_CONSUMED);
            replayed.setConsumedByOperationId("feed-op-1");
            when(entitlementMapper.selectOne(any())).thenReturn(replayed);

            Optional<Long> consumed = service.consumeOneForFeed(USER_ID, 5L, "feed-op-1");

            assertThat(consumed).contains(71L);
            verify(entitlementMapper, never()).update(any(), any());
        }

        @Test
        @DisplayName("无可用权益 → empty，不应用双倍")
        void consume_noUsableEntitlement_returnsEmpty() {
            when(entitlementMapper.selectOne(any()))
                    .thenReturn((PetFeedEntitlement) null)
                    .thenReturn(null);

            assertThat(service.consumeOneForFeed(USER_ID, 5L, "feed-op-2")).isEmpty();
        }

        @Test
        @DisplayName("CAS 竞争 0 行 → 换下一条候选重试，避免并发漏占")
        void consume_casRace_retriesNextCandidate() {
            when(entitlementMapper.selectOne(any()))
                    .thenReturn((PetFeedEntitlement) null)
                    .thenReturn(entitlement(71L, PetFeedEntitlement.STATUS_AVAILABLE))
                    .thenReturn(entitlement(72L, PetFeedEntitlement.STATUS_AVAILABLE));
            when(entitlementMapper.update(any(), any())).thenReturn(0, 1);

            Optional<Long> consumed = service.consumeOneForFeed(USER_ID, 5L, "feed-op-3");

            assertThat(consumed).contains(72L);
            verify(entitlementMapper, times(2)).update(any(), any());
        }
    }
}
