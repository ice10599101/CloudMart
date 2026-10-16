package com.cloudmart.order.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.async.EventEnvelope;
import com.cloudmart.common.async.outbox.OutboxService;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.order.entity.AfterSaleCase;
import com.cloudmart.order.entity.Order;
import com.cloudmart.order.entity.OrderItem;
import com.cloudmart.order.repository.AfterSaleCaseEventMapper;
import com.cloudmart.order.repository.AfterSaleCaseMapper;
import com.cloudmart.order.repository.OrderItemMapper;
import com.cloudmart.order.repository.OrderMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T04/TRADE-01 售后按明细 case 结算（资金安全）：
 * ① 行级——两种退款类型一致受行实付快照约束（整数分均分 + 尾差归最后数量段 +
 *    行剩余可退金额兜底；无快照回退原价的旧分支已删除）；
 * ② 订单级——订单行锁内其他案件占用（APPROVED+REFUNDED）+ 本案 ≤ 订单实付；
 * ③ 申请——锁订单行后校验有效占用（整单/按行互斥）+ COMPLETED 窗口 +
 *    请求事实键同键同参重放/同键异参 409；
 * ④ 退款号服务端派生 RFC{caseId}；REFUND_ONLY 审批同事务写 AFTER_SALE_REFUND_SUBMIT Outbox。
 */
@DisplayName("AfterSaleCaseServiceImpl T04/TRADE-01 结算校验")
class AfterSaleCaseRefundAmountTest {

    private AfterSaleCaseMapper caseMapper;
    private AfterSaleCaseEventMapper eventMapper;
    private OrderMapper orderMapper;
    private OrderItemMapper orderItemMapper;
    private OutboxService outboxService;
    private com.cloudmart.order.config.AfterSaleProperties afterSaleProperties;
    private AfterSaleCaseServiceImpl service;

    private AfterSaleCase caseEntity;
    private Order order;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        for (Class<?> clazz : new Class<?>[]{AfterSaleCase.class, OrderItem.class, Order.class}) {
            if (TableInfoHelper.getTableInfo(clazz) == null) {
                MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
                TableInfoHelper.initTableInfo(assistant, clazz);
            }
        }
    }

    @BeforeEach
    void setUp() {
        caseMapper = mock(AfterSaleCaseMapper.class);
        eventMapper = mock(AfterSaleCaseEventMapper.class);
        orderMapper = mock(OrderMapper.class);
        orderItemMapper = mock(OrderItemMapper.class);
        outboxService = mock(OutboxService.class);
        afterSaleProperties = new com.cloudmart.order.config.AfterSaleProperties();
        service = new AfterSaleCaseServiceImpl(caseMapper, eventMapper, orderMapper, orderItemMapper,
                outboxService, new ObjectMapper(), afterSaleProperties);

        caseEntity = new AfterSaleCase();
        caseEntity.setId(11L);
        caseEntity.setCaseNo("AS20261003001");
        caseEntity.setOrderId(9001L);
        caseEntity.setUserId(42L);
        caseEntity.setStatus(AfterSaleCase.STATUS_PENDING);
        caseEntity.setQuantity(1);

        order = new Order();
        order.setId(9001L);
        order.setOrderNo("NO9001");
        order.setPayAmount(new BigDecimal("1998.00"));
        when(orderMapper.selectByIdForUpdate(9001L)).thenReturn(order);
    }

    /** 行案件（PENDING）桩：selectById 返回该案件，供多次审批阶段复用同一实体模板 */
    private void stubItemCase(Long caseId, AfterSaleCase template, int quantity) {
        AfterSaleCase pending = new AfterSaleCase();
        pending.setId(caseId);
        pending.setCaseNo("AS" + caseId);
        pending.setOrderId(template.getOrderId());
        pending.setUserId(template.getUserId());
        pending.setItemId(5L);
        pending.setType(AfterSaleCase.TYPE_RETURN_REFUND);
        pending.setQuantity(quantity);
        pending.setStatus(AfterSaleCase.STATUS_PENDING);
        when(caseMapper.selectById(caseId)).thenReturn(pending);
    }

    /** 已受理行案件（APPROVED）——作为后续审批的"该行已占用"事实 */
    private AfterSaleCase approvedItemCase(Long caseId, int quantity, String amount) {
        AfterSaleCase approved = new AfterSaleCase();
        approved.setId(caseId);
        approved.setOrderId(9001L);
        approved.setUserId(42L);
        approved.setItemId(5L);
        approved.setQuantity(quantity);
        approved.setStatus(AfterSaleCase.STATUS_APPROVED);
        approved.setRefundAmount(new BigDecimal(amount));
        return approved;
    }

    @Test
    @DisplayName("退货退款批准金额超过 行可退上限（数量×明细实付单价）→ 拒绝")
    void approve_exceedsItemCap_rejected() {
        caseEntity.setType(AfterSaleCase.TYPE_RETURN_REFUND);
        caseEntity.setItemId(5L);
        caseEntity.setQuantity(1);
        when(caseMapper.selectById(11L)).thenReturn(caseEntity);

        OrderItem item = new OrderItem();
        item.setId(5L);
        item.setPrice(new BigDecimal("999.00"));
        item.setPayAmount(new BigDecimal("899.10"));
        item.setQuantity(1);
        when(orderItemMapper.selectById(5L)).thenReturn(item);

        assertThatThrownBy(() -> service.approve(1L, 11L, new BigDecimal("999.00")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "AFTER_SALE_AMOUNT_EXCEEDED");
        verify(caseMapper, never()).approve(any(), any(), any(), any());
    }

    @Test
    @DisplayName("同订单其他案件已占用退款，叠加本案超过订单实付 → 拒绝")
    void approve_cumulativeExceedsPayAmount_rejected() {
        caseEntity.setType(AfterSaleCase.TYPE_REFUND_ONLY);
        when(caseMapper.selectById(11L)).thenReturn(caseEntity);

        AfterSaleCase other = new AfterSaleCase();
        other.setId(12L);
        other.setStatus(AfterSaleCase.STATUS_REFUNDED);
        other.setRefundAmount(new BigDecimal("1500.00"));
        when(caseMapper.selectList(any())).thenReturn(List.of(other));

        assertThatThrownBy(() -> service.approve(1L, 11L, new BigDecimal("600.00")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "AFTER_SALE_AMOUNT_EXCEEDED")
                .hasMessageContaining("1500.00");
        verify(caseMapper, never()).approve(any(), any(), any(), any());
    }

    @Test
    @DisplayName("金额在行上限与订单实付余量内 → 正常受理，退款号=RFC{caseId}，仅退款写提交 Outbox")
    void approve_withinLimits_approved() {
        caseEntity.setType(AfterSaleCase.TYPE_RETURN_REFUND);
        caseEntity.setItemId(5L);
        caseEntity.setQuantity(2);
        when(caseMapper.selectById(11L)).thenReturn(caseEntity);

        OrderItem item = new OrderItem();
        item.setId(5L);
        item.setPrice(new BigDecimal("999.00"));
        item.setPayAmount(new BigDecimal("999.00"));
        item.setQuantity(2);
        when(orderItemMapper.selectById(5L)).thenReturn(item);
        when(caseMapper.selectList(any())).thenReturn(List.of());
        when(caseMapper.approve(eq(11L), any(), any(), eq(1L))).thenReturn(1);
        when(eventMapper.selectList(any())).thenReturn(List.of());

        var vo = service.approve(1L, 11L, new BigDecimal("999.00"));

        assertThat(vo).isNotNull();
        verify(caseMapper).approve(eq(11L), eq(new BigDecimal("999.00")), eq("RFC11"), eq(1L));
    }

    @Test
    @DisplayName("REFUND_ONLY 受理 → 同事务写 AFTER_SALE_REFUND_SUBMIT Outbox（案件事实驱动退款提交）")
    void approve_refundOnly_recordsSubmitEvent() {
        caseEntity.setType(AfterSaleCase.TYPE_REFUND_ONLY);
        // mock 不会回写 CAS 结果：受理后的重读行由桩提供（refundNo/amount 已落库）
        caseEntity.setRefundNo("RFC11");
        caseEntity.setRefundAmount(new BigDecimal("500.00"));
        when(caseMapper.selectById(11L)).thenReturn(caseEntity);
        when(caseMapper.selectList(any())).thenReturn(List.of());
        when(caseMapper.approve(eq(11L), any(), any(), eq(1L))).thenReturn(1);
        when(eventMapper.selectList(any())).thenReturn(List.of());

        service.approve(1L, 11L, new BigDecimal("500.00"));

        ArgumentCaptor<EventEnvelope> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxService).record(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("AFTER_SALE_REFUND_SUBMIT");
        assertThat(captor.getValue().payload()).contains("\"refundNo\":\"RFC11\"");
    }

    @Test
    @DisplayName("订单行锁参与额度串行化：approve 先锁订单（selectByIdForUpdate）")
    void approve_locksOrderRow() {
        caseEntity.setType(AfterSaleCase.TYPE_REFUND_ONLY);
        when(caseMapper.selectById(11L)).thenReturn(caseEntity);
        when(caseMapper.selectList(any())).thenReturn(List.of());
        when(caseMapper.approve(eq(11L), any(), any(), eq(1L))).thenReturn(1);
        when(eventMapper.selectList(any())).thenReturn(List.of());

        service.approve(1L, 11L, new BigDecimal("100.00"));

        verify(orderMapper).selectByIdForUpdate(9001L);
    }

    @Test
    @DisplayName("非 PENDING 案件受理 → AFTER_SALE_STATUS_ERROR")
    void approve_notPending_rejected() {
        caseEntity.setStatus(AfterSaleCase.STATUS_APPROVED);
        when(caseMapper.selectById(11L)).thenReturn(caseEntity);

        assertThatThrownBy(() -> service.approve(1L, 11L, new BigDecimal("10.00")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "AFTER_SALE_STATUS_ERROR");
        verify(orderMapper, never()).selectByIdForUpdate(anyLong());
    }

    @Test
    @DisplayName("R11：非零元订单不允许 0 元退款（只能走真实渠道正金额）")
    void approve_zeroAmountOnNonzeroOrder_rejected() {
        caseEntity.setType(AfterSaleCase.TYPE_REFUND_ONLY);
        when(caseMapper.selectById(11L)).thenReturn(caseEntity);

        assertThatThrownBy(() -> service.approve(1L, 11L, BigDecimal.ZERO))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "AFTER_SALE_AMOUNT_INVALID");
        verify(caseMapper, never()).approve(any(), any(), any(), any());
    }

    @Test
    @DisplayName("R11：负金额一律拒绝")
    void approve_negativeAmount_rejected() {
        caseEntity.setType(AfterSaleCase.TYPE_REFUND_ONLY);
        when(caseMapper.selectById(11L)).thenReturn(caseEntity);

        assertThatThrownBy(() -> service.approve(1L, 11L, new BigDecimal("-0.01")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "AFTER_SALE_AMOUNT_INVALID");
    }

    @Test
    @DisplayName("R11：零元订单 0 元退款可受理——不调用收费渠道，仅出 AFTER_SALE_REFUND_SUBMIT"
            + "（消费者识别金额 0 走域内结算）")
    void approve_zeroAmountOnZeroPayOrder_approvedWithoutChannel() {
        order.setPayAmount(BigDecimal.ZERO);
        caseEntity.setType(AfterSaleCase.TYPE_REFUND_ONLY);
        caseEntity.setRefundNo("RFC11");
        caseEntity.setRefundAmount(BigDecimal.ZERO);
        when(caseMapper.selectById(11L)).thenReturn(caseEntity);
        when(caseMapper.selectList(any())).thenReturn(List.of());
        when(caseMapper.approve(eq(11L), eq(BigDecimal.ZERO), eq("RFC11"), eq(1L))).thenReturn(1);
        when(eventMapper.selectList(any())).thenReturn(List.of());

        service.approve(1L, 11L, BigDecimal.ZERO);

        verify(caseMapper).approve(eq(11L), eq(BigDecimal.ZERO), eq("RFC11"), eq(1L));
        ArgumentCaptor<EventEnvelope> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxService).record(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("AFTER_SALE_REFUND_SUBMIT");
    }

    @Test
    @DisplayName("订单缺失或实付金额为空 → 拒绝受理（fail-closed）")
    void approve_orderMissing_rejected() {
        caseEntity.setType(AfterSaleCase.TYPE_REFUND_ONLY);
        when(caseMapper.selectById(11L)).thenReturn(caseEntity);
        when(orderMapper.selectByIdForUpdate(9001L)).thenReturn(null);

        assertThatThrownBy(() -> service.approve(1L, 11L, new BigDecimal("10.00")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ORDER_NOT_FOUND");
        verify(caseMapper, never()).approve(any(), any(), any(), any());
    }

    @Test
    @DisplayName("T04/TRADE-01 数量占用：同明细已占用数量 + 本次 > 明细数量 → 拒绝申请")
    void apply_itemQuantityOccupied_rejected() {
        Order applyOrder = new Order();
        applyOrder.setId(9001L);
        applyOrder.setUserId(42L);
        applyOrder.setStatus("SHIPPED");
        when(orderMapper.selectByIdForUpdate(9001L)).thenReturn(applyOrder);

        OrderItem item = new OrderItem();
        item.setId(5L);
        item.setOrderId(9001L);
        item.setPrice(new BigDecimal("100.00"));
        item.setPayAmount(new BigDecimal("100.00"));
        item.setQuantity(2);
        when(orderItemMapper.selectById(5L)).thenReturn(item);

        AfterSaleCase occupying = new AfterSaleCase();
        occupying.setId(12L);
        occupying.setItemId(5L);
        occupying.setQuantity(1);
        occupying.setStatus(AfterSaleCase.STATUS_REFUNDED);
        when(caseMapper.selectList(any())).thenReturn(List.of(occupying));

        var request = new com.cloudmart.order.dto.CreateAfterSaleRequest(
                9001L, 5L, AfterSaleCase.TYPE_RETURN_REFUND, "破损", null, 2);

        assertThatThrownBy(() -> service.apply(42L, request, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "AFTER_SALE_QUANTITY_EXCEEDED");
        verify(caseMapper, never()).insert(any(AfterSaleCase.class));
    }

    @Test
    @DisplayName("TRADE-01：apply 读取占用前先锁订单行（并发申请串行化锚点）")
    void apply_locksOrderRowBeforeOccupancy() {
        Order applyOrder = new Order();
        applyOrder.setId(9001L);
        applyOrder.setUserId(42L);
        applyOrder.setStatus("PAID");
        when(orderMapper.selectByIdForUpdate(9001L)).thenReturn(applyOrder);
        when(caseMapper.selectList(any())).thenReturn(List.of());
        when(caseMapper.insert(any(AfterSaleCase.class))).thenAnswer(inv -> {
            inv.getArgument(0, AfterSaleCase.class).setId(21L);
            return 1;
        });

        var request = new com.cloudmart.order.dto.CreateAfterSaleRequest(
                9001L, null, AfterSaleCase.TYPE_REFUND_ONLY, "不想要了", null, 0);
        service.apply(42L, request, null);

        verify(orderMapper).selectByIdForUpdate(9001L);
        verify(caseMapper).insert(any(AfterSaleCase.class));
    }

    @Test
    @DisplayName("TRADE-01：整单案件有效占用时（PENDING/APPROVED/REFUNDED）不允许另开行案件")
    void apply_wholeOrderActive_blocksLineCase() {
        Order applyOrder = new Order();
        applyOrder.setId(9001L);
        applyOrder.setUserId(42L);
        applyOrder.setStatus("PAID");
        when(orderMapper.selectByIdForUpdate(9001L)).thenReturn(applyOrder);

        AfterSaleCase whole = new AfterSaleCase();
        whole.setId(12L);
        whole.setItemId(null);
        whole.setStatus(AfterSaleCase.STATUS_APPROVED);
        when(caseMapper.selectList(any())).thenReturn(List.of(whole));

        var request = new com.cloudmart.order.dto.CreateAfterSaleRequest(
                9001L, 5L, AfterSaleCase.TYPE_REFUND_ONLY, "少件", null, 1);

        assertThatThrownBy(() -> service.apply(42L, request, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "AFTER_SALE_DUPLICATE");
        verify(caseMapper, never()).insert(any(AfterSaleCase.class));
    }

    @Test
    @DisplayName("TRADE-01：已有行案件占用时不允许另开整单案件（重叠退款防护）")
    void apply_lineActive_blocksWholeOrderCase() {
        Order applyOrder = new Order();
        applyOrder.setId(9001L);
        applyOrder.setUserId(42L);
        applyOrder.setStatus("PAID");
        when(orderMapper.selectByIdForUpdate(9001L)).thenReturn(applyOrder);

        AfterSaleCase line = new AfterSaleCase();
        line.setId(12L);
        line.setItemId(5L);
        line.setQuantity(1);
        line.setStatus(AfterSaleCase.STATUS_REFUNDED);
        when(caseMapper.selectList(any())).thenReturn(List.of(line));

        var request = new com.cloudmart.order.dto.CreateAfterSaleRequest(
                9001L, null, AfterSaleCase.TYPE_REFUND_ONLY, "全退", null, 0);

        assertThatThrownBy(() -> service.apply(42L, request, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "AFTER_SALE_DUPLICATE");
    }

    @Test
    @DisplayName("TRADE-01：同键同参重放 → 返回原案件，不重复建案/重复占用")
    void apply_requestKeyReplay_returnsOriginal() {
        Order applyOrder = new Order();
        applyOrder.setId(9001L);
        applyOrder.setUserId(42L);
        applyOrder.setStatus("PAID");
        when(orderMapper.selectByIdForUpdate(9001L)).thenReturn(applyOrder);
        when(caseMapper.selectList(any())).thenReturn(List.of());
        when(caseMapper.insert(any(AfterSaleCase.class))).thenAnswer(inv -> {
            inv.getArgument(0, AfterSaleCase.class).setId(21L);
            return 1;
        });
        when(eventMapper.selectList(any())).thenReturn(List.of());

        var request = new com.cloudmart.order.dto.CreateAfterSaleRequest(
                9001L, null, AfterSaleCase.TYPE_REFUND_ONLY, "不想要了", null, 0);
        service.apply(42L, request, "key-after-sale-1");

        ArgumentCaptor<AfterSaleCase> inserted = ArgumentCaptor.forClass(AfterSaleCase.class);
        verify(caseMapper).insert(inserted.capture());
        AfterSaleCase saved = inserted.getValue();
        assertThat(saved.getRequestKey()).isEqualTo("key-after-sale-1");
        assertThat(saved.getPayloadHash()).isNotBlank();

        // 同键同参重放：命中既有案件直接返回
        when(caseMapper.selectOne(any())).thenReturn(saved);
        var replayed = service.apply(42L, request, "key-after-sale-1");

        assertThat(replayed.id()).isEqualTo(21L);
        verify(caseMapper, times(1)).insert(any(AfterSaleCase.class));
    }

    @Test
    @DisplayName("TRADE-01：同键异参 → AFTER_SALE_REQUEST_CONFLICT（禁止换参重放）")
    void apply_requestKeyDifferentPayload_conflict() {
        Order applyOrder = new Order();
        applyOrder.setId(9001L);
        applyOrder.setUserId(42L);
        applyOrder.setStatus("PAID");
        when(orderMapper.selectByIdForUpdate(9001L)).thenReturn(applyOrder);

        AfterSaleCase existing = new AfterSaleCase();
        existing.setId(21L);
        existing.setOrderId(9001L);
        existing.setUserId(42L);
        existing.setRequestKey("key-after-sale-2");
        existing.setPayloadHash("stale-hash-of-other-payload");
        existing.setStatus(AfterSaleCase.STATUS_PENDING);
        when(caseMapper.selectOne(any())).thenReturn(existing);

        var request = new com.cloudmart.order.dto.CreateAfterSaleRequest(
                9001L, null, AfterSaleCase.TYPE_REFUND_ONLY, "换个理由", null, 0);

        assertThatThrownBy(() -> service.apply(42L, request, "key-after-sale-2"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "AFTER_SALE_REQUEST_CONFLICT");
        verify(caseMapper, never()).insert(any(AfterSaleCase.class));
    }

    @Test
    @DisplayName("TRADE-01：COMPLETED 订单确认收货 7 天内可申请（窗口内允许）")
    void apply_completedWithinWindow_allowed() {
        Order applyOrder = new Order();
        applyOrder.setId(9001L);
        applyOrder.setUserId(42L);
        applyOrder.setStatus("COMPLETED");
        applyOrder.setCompletedAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusDays(1));
        when(orderMapper.selectByIdForUpdate(9001L)).thenReturn(applyOrder);
        when(caseMapper.selectList(any())).thenReturn(List.of());
        when(caseMapper.insert(any(AfterSaleCase.class))).thenAnswer(inv -> {
            inv.getArgument(0, AfterSaleCase.class).setId(21L);
            return 1;
        });

        var request = new com.cloudmart.order.dto.CreateAfterSaleRequest(
                9001L, null, AfterSaleCase.TYPE_REFUND_ONLY, "售后", null, 0);
        var vo = service.apply(42L, request, null);

        assertThat(vo).isNotNull();
    }

    @Test
    @DisplayName("TRADE-01：COMPLETED 订单超出确认收货 7 天 → AFTER_SALE_WINDOW_EXPIRED")
    void apply_completedBeyondWindow_rejected() {
        Order applyOrder = new Order();
        applyOrder.setId(9001L);
        applyOrder.setUserId(42L);
        applyOrder.setStatus("COMPLETED");
        applyOrder.setCompletedAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusDays(8));
        when(orderMapper.selectByIdForUpdate(9001L)).thenReturn(applyOrder);

        var request = new com.cloudmart.order.dto.CreateAfterSaleRequest(
                9001L, null, AfterSaleCase.TYPE_REFUND_ONLY, "售后", null, 0);

        assertThatThrownBy(() -> service.apply(42L, request, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "AFTER_SALE_WINDOW_EXPIRED");
        verify(caseMapper, never()).insert(any(AfterSaleCase.class));
    }

    @Test
    @DisplayName("TRADE-01：PAID 未发货订单只能仅退款（RETURN_REFUND 拒绝）")
    void apply_paidReturnRefund_rejected() {
        Order applyOrder = new Order();
        applyOrder.setId(9001L);
        applyOrder.setUserId(42L);
        applyOrder.setStatus("PAID");
        when(orderMapper.selectByIdForUpdate(9001L)).thenReturn(applyOrder);

        var request = new com.cloudmart.order.dto.CreateAfterSaleRequest(
                9001L, 5L, AfterSaleCase.TYPE_RETURN_REFUND, "退货", null, 1);

        assertThatThrownBy(() -> service.apply(42L, request, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "AFTER_SALE_TYPE_INVALID");
    }

    @Test
    @DisplayName("TRADE-01：REFUND_ONLY 带 itemId 同样受行上限约束（原实现只约束退货退款）")
    void approve_refundOnlyItemCap_enforced() {
        caseEntity.setType(AfterSaleCase.TYPE_REFUND_ONLY);
        caseEntity.setItemId(5L);
        caseEntity.setQuantity(1);
        when(caseMapper.selectById(11L)).thenReturn(caseEntity);

        OrderItem item = new OrderItem();
        item.setId(5L);
        item.setPayAmount(new BigDecimal("100.00"));
        item.setQuantity(1);
        when(orderItemMapper.selectById(5L)).thenReturn(item);
        when(caseMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.approve(1L, 11L, new BigDecimal("150.00")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "AFTER_SALE_AMOUNT_EXCEEDED");
        verify(caseMapper, never()).approve(any(), any(), any(), any());
    }

    @Test
    @DisplayName("TRADE-01：行实付快照缺失 → fail-closed（无快照回退原价的旧分支已删除）")
    void approve_itemWithoutPayAmountSnapshot_rejected() {
        caseEntity.setType(AfterSaleCase.TYPE_RETURN_REFUND);
        caseEntity.setItemId(5L);
        when(caseMapper.selectById(11L)).thenReturn(caseEntity);

        OrderItem item = new OrderItem();
        item.setId(5L);
        item.setPrice(new BigDecimal("999.00"));
        item.setPayAmount(null);
        item.setQuantity(1);
        when(orderItemMapper.selectById(5L)).thenReturn(item);

        assertThatThrownBy(() -> service.approve(1L, 11L, new BigDecimal("100.00")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "AFTER_SALE_AMOUNT_INVALID");
    }

    @Test
    @DisplayName("TRADE-01 尾差：10 元 3 件分批退（1+1+1）累计恰好 10.00，任何阶段不超行实付")
    void approve_centsAllocation_tailGoesToLastSegment() {
        OrderItem item = new OrderItem();
        item.setId(5L);
        item.setPayAmount(new BigDecimal("10.00"));
        item.setQuantity(3);
        when(orderItemMapper.selectById(5L)).thenReturn(item);
        when(eventMapper.selectList(any())).thenReturn(List.of());
        // 三笔审批全部合法走 CAS
        when(caseMapper.approve(eq(11L), any(), any(), eq(1L))).thenReturn(1);
        when(caseMapper.approve(eq(12L), any(), any(), eq(1L))).thenReturn(1);
        when(caseMapper.approve(eq(13L), any(), any(), eq(1L))).thenReturn(1);

        // 第 1 件：均分 3.33
        stubItemCase(11L, caseEntity, 1);
        when(caseMapper.selectList(any())).thenReturn(List.of());
        service.approve(1L, 11L, new BigDecimal("3.33"));

        // 第 2 件：尝试 3.34 超均分额度 → 拒绝（尾差不属于非收尾段）
        AfterSaleCase first = approvedItemCase(11L, 1, "3.33");
        stubItemCase(12L, caseEntity, 1);
        when(caseMapper.selectList(any())).thenReturn(List.of(first));
        assertThatThrownBy(() -> service.approve(1L, 12L, new BigDecimal("3.34")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "AFTER_SALE_AMOUNT_EXCEEDED");

        // 第 2 件按 3.33 通过
        service.approve(1L, 12L, new BigDecimal("3.33"));

        // 第 3 件（最后剩余数量段）：可取尾差 0.01 → 3.34
        AfterSaleCase second = approvedItemCase(12L, 1, "3.33");
        stubItemCase(13L, caseEntity, 1);
        when(caseMapper.selectList(any())).thenReturn(List.of(first, second));
        service.approve(1L, 13L, new BigDecimal("3.34"));

        ArgumentCaptor<BigDecimal> amounts = ArgumentCaptor.forClass(BigDecimal.class);
        verify(caseMapper, times(3)).approve(any(), amounts.capture(), any(), any());
        assertThat(amounts.getAllValues().stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("TRADE-01：单案覆盖整行可取全部含尾差（3/3 件 → 10.00 上限）")
    void approve_singleCaseWholeLine_allowsFullAmount() {
        caseEntity.setType(AfterSaleCase.TYPE_RETURN_REFUND);
        caseEntity.setItemId(5L);
        caseEntity.setQuantity(3);
        when(caseMapper.selectById(11L)).thenReturn(caseEntity);

        OrderItem item = new OrderItem();
        item.setId(5L);
        item.setPayAmount(new BigDecimal("10.00"));
        item.setQuantity(3);
        when(orderItemMapper.selectById(5L)).thenReturn(item);
        when(caseMapper.selectList(any())).thenReturn(List.of());
        when(caseMapper.approve(eq(11L), any(), any(), eq(1L))).thenReturn(1);
        when(eventMapper.selectList(any())).thenReturn(List.of());

        // 超出行实付 → 拒绝
        assertThatThrownBy(() -> service.approve(1L, 11L, new BigDecimal("10.01")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "AFTER_SALE_AMOUNT_EXCEEDED");
        // 恰好行实付（含尾差）→ 通过
        service.approve(1L, 11L, new BigDecimal("10.00"));
        verify(caseMapper).approve(eq(11L), eq(new BigDecimal("10.00")), eq("RFC11"), eq(1L));
    }

    // ---------------- TRADE-01 eligibility ----------------

    @Test
    @DisplayName("eligibility：SHIPPED 按商品项返回剩余数量/剩余可退金额与可选类型")
    void eligibility_itemRemaining() {
        Order order = new Order();
        order.setId(9001L);
        order.setUserId(42L);
        order.setStatus("SHIPPED");
        order.setPayAmount(new BigDecimal("10.00"));
        when(orderMapper.selectById(9001L)).thenReturn(order);
        OrderItem item = new OrderItem();
        item.setId(5L);
        item.setOrderId(9001L);
        item.setPayAmount(new BigDecimal("10.00"));
        item.setQuantity(3);
        when(orderItemMapper.selectById(5L)).thenReturn(item);

        AfterSaleCase refunded = new AfterSaleCase();
        refunded.setId(12L);
        refunded.setItemId(5L);
        refunded.setQuantity(1);
        refunded.setStatus(AfterSaleCase.STATUS_REFUNDED);
        refunded.setRefundAmount(new BigDecimal("3.33"));
        when(caseMapper.selectList(any())).thenReturn(List.of(refunded));

        var vo = service.eligibility(42L, 9001L, 5L);

        assertThat(vo.applicable()).isTrue();
        assertThat(vo.allowedTypes()).containsExactly("RETURN_REFUND", "REFUND_ONLY");
        assertThat(vo.remainingQuantity()).isEqualTo(2);
        assertThat(vo.remainingRefundAmount()).isEqualByComparingTo("6.67");
        assertThat(vo.applyDeadlineAt()).isNull();
        assertThat(vo.rejectReason()).isNull();
    }

    @Test
    @DisplayName("eligibility：PAID 整单 → 仅退款类型与订单剩余实付")
    void eligibility_wholeOrderOnlyRefundTypes() {
        Order order = new Order();
        order.setId(9001L);
        order.setUserId(42L);
        order.setStatus("PAID");
        order.setPayAmount(new BigDecimal("1998.00"));
        when(orderMapper.selectById(9001L)).thenReturn(order);
        when(caseMapper.selectList(any())).thenReturn(List.of());

        var vo = service.eligibility(42L, 9001L, null);

        assertThat(vo.applicable()).isTrue();
        assertThat(vo.allowedTypes()).containsExactly("REFUND_ONLY");
        assertThat(vo.remainingQuantity()).isNull();
        assertThat(vo.remainingRefundAmount()).isEqualByComparingTo("1998.00");
    }

    @Test
    @DisplayName("eligibility：COMPLETED 超窗 → AFTER_SALE_WINDOW_EXPIRED 且返回截止时间")
    void eligibility_completedExpired_returnsDeadline() {
        Order order = new Order();
        order.setId(9001L);
        order.setUserId(42L);
        order.setStatus("COMPLETED");
        order.setCompletedAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusDays(8));
        order.setPayAmount(new BigDecimal("10.00"));
        when(orderMapper.selectById(9001L)).thenReturn(order);
        when(caseMapper.selectList(any())).thenReturn(List.of());

        var vo = service.eligibility(42L, 9001L, null);

        assertThat(vo.applicable()).isFalse();
        assertThat(vo.rejectReason()).isEqualTo("AFTER_SALE_WINDOW_EXPIRED");
        assertThat(vo.applyDeadlineAt()).isNotNull();
    }

    @Test
    @DisplayName("TRADE-01：markRefundedReturning CAS 命中返回案件（含金额），未命中幂等返回 null")
    void markRefundedReturning_idempotent() {
        when(caseMapper.markRefunded("RFC11")).thenReturn(1);
        caseEntity.setRefundNo("RFC11");
        caseEntity.setStatus(AfterSaleCase.STATUS_REFUNDED);
        caseEntity.setRefundAmount(new BigDecimal("100.00"));
        when(caseMapper.selectOne(any())).thenReturn(caseEntity);
        when(eventMapper.selectList(any())).thenReturn(List.of());

        var refunded = service.markRefundedReturning("RFC11");
        assertThat(refunded).isNotNull();
        assertThat(refunded.getRefundAmount()).isEqualByComparingTo("100.00");

        when(caseMapper.markRefunded("RFC11")).thenReturn(0);
        assertThat(service.markRefundedReturning("RFC11")).isNull();
    }

    @Test
    @DisplayName("退款号派生：RFC{caseId}（orders.id 为雪花，与 case 自增空间不重叠）")
    void refundNo_derivedFromCaseId() {
        assertThat(AfterSaleCaseServiceImpl.refundNoOf(11L)).isEqualTo("RFC11");
        assertThat(AfterSaleCaseServiceImpl.refundNoOf(123456789L)).isEqualTo("RFC123456789");
    }
}
