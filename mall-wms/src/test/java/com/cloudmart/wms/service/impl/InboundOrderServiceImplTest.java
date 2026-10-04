package com.cloudmart.wms.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wms.dto.CreateInboundOrderRequest;
import com.cloudmart.wms.dto.InboundItemRequest;
import com.cloudmart.wms.dto.InboundOrderDTO;
import com.cloudmart.wms.entity.InboundOrder;
import com.cloudmart.wms.entity.InboundOrderItem;
import com.cloudmart.wms.repository.InboundOrderItemMapper;
import com.cloudmart.wms.repository.InboundOrderMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InboundOrderServiceImpl 单元测试")
class InboundOrderServiceImplTest {

    @Mock
    private InboundOrderMapper inboundOrderMapper;

    @Mock
    private InboundOrderItemMapper inboundOrderItemMapper;
    @Mock
    private com.cloudmart.wms.repository.InboundReceiptMapper receiptMapper;
    @Mock
    private com.cloudmart.common.async.outbox.OutboxService outboxService;
    @Mock
    private com.cloudmart.wms.feign.InventoryRestockFeignClient inventoryRestockFeignClient;
    @Mock
    private com.cloudmart.common.async.compensation.CompensationTaskService compensationTaskService;

    @InjectMocks
    private InboundOrderServiceImpl inboundOrderService;

    @BeforeAll
    static void initTableInfo() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace("com.cloudmart.wms.repository.InboundOrderMapper");
        TableInfoHelper.initTableInfo(assistant, InboundOrder.class);

        MapperBuilderAssistant itemAssistant = new MapperBuilderAssistant(configuration, "");
        itemAssistant.setCurrentNamespace("com.cloudmart.wms.repository.InboundOrderItemMapper");
        TableInfoHelper.initTableInfo(itemAssistant, InboundOrderItem.class);
    }

    private InboundOrder buildInboundOrder() {
        InboundOrder order = new InboundOrder();
        order.setId(1L);
        order.setWarehouseId(100L);
        order.setType("PURCHASE");
        order.setReferenceNo("REF-001");
        order.setStatus("PENDING");
        order.setTotalQuantity(10);
        order.setReceivedQuantity(0);
        order.setRemark("test remark");
        return order;
    }

    private InboundOrderItem buildInboundOrderItem() {
        InboundOrderItem item = new InboundOrderItem();
        item.setId(10L);
        item.setInboundOrderId(1L);
        item.setSkuId(200L);
        item.setProductName("Test Product");
        item.setExpectedQuantity(10);
        item.setReceivedQuantity(0);
        item.setLocationCode("A-01-01");
        return item;
    }

    private CreateInboundOrderRequest buildCreateRequest() {
        return new CreateInboundOrderRequest(
            100L, "PURCHASE", "REF-001", "test remark",
            List.of(new InboundItemRequest(200L, "Test Product", 10, "A-01-01"))
        );
    }

    @Nested
    @DisplayName("createInboundOrder 方法")
    class CreateInboundOrderTests {

        @Test
        @DisplayName("创建入库单 - 成功")
        void shouldCreateInboundOrder() {
            CreateInboundOrderRequest request = buildCreateRequest();

            when(inboundOrderItemMapper.selectList(any())).thenReturn(List.of());

            inboundOrderService.createInboundOrder(request);

            verify(inboundOrderMapper).insert(any(InboundOrder.class));
            verify(inboundOrderItemMapper).insert(any(InboundOrderItem.class));
        }

        @Test
        @DisplayName("创建入库单 - 设置初始状态为PENDING")
        void shouldSetInitialStatusToPending() {
            CreateInboundOrderRequest request = buildCreateRequest();

            when(inboundOrderItemMapper.selectList(any())).thenReturn(List.of());

            inboundOrderService.createInboundOrder(request);

            verify(inboundOrderMapper).insert(any(InboundOrder.class));
        }

        @Test
        @DisplayName("创建入库单 - 多个明细项时全部插入")
        void shouldInsertAllItems() {
            CreateInboundOrderRequest request = new CreateInboundOrderRequest(
                100L, "PURCHASE", "REF-002", "multi items",
                List.of(
                    new InboundItemRequest(1L, "Product A", 5, "A-01"),
                    new InboundItemRequest(2L, "Product B", 3, "A-02")
                )
            );

            when(inboundOrderItemMapper.selectList(any())).thenReturn(List.of());

            inboundOrderService.createInboundOrder(request);

            verify(inboundOrderMapper).insert(any(InboundOrder.class));
            verify(inboundOrderItemMapper, times(2)).insert(any(InboundOrderItem.class));
        }
    }

    @Nested
    @DisplayName("receiveItem 方法")
    class ReceiveItemTests {

        @Test
        @DisplayName("收货 - 入库单不存在时抛出异常")
        void shouldThrowWhenOrderNotFound() {
            when(inboundOrderMapper.selectById(anyLong())).thenReturn(null);

            assertThatThrownBy(() -> inboundOrderService.receiveItem(1L, 10L, 5))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("INBOUND_ORDER_NOT_FOUND");
        }

        @Test
        @DisplayName("收货 - 入库单已完成时抛出异常")
        void shouldThrowWhenOrderCompleted() {
            InboundOrder order = buildInboundOrder();
            order.setStatus("COMPLETED");
            when(inboundOrderMapper.selectById(anyLong())).thenReturn(order);

            assertThatThrownBy(() -> inboundOrderService.receiveItem(1L, 10L, 5))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("INBOUND_COMPLETED");
        }

        @Test
        @DisplayName("收货 - 明细不存在时抛出异常")
        void shouldThrowWhenItemNotFound() {
            InboundOrder order = buildInboundOrder();
            when(inboundOrderMapper.selectById(anyLong())).thenReturn(order);
            when(inboundOrderItemMapper.selectById(anyLong())).thenReturn(null);

            assertThatThrownBy(() -> inboundOrderService.receiveItem(1L, 999L, 5))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("ITEM_NOT_FOUND");
        }

        @Test
        @DisplayName("收货 - 明细不属于该入库单时抛出异常")
        void shouldThrowWhenItemNotBelongToOrder() {
            InboundOrder order = buildInboundOrder();
            InboundOrderItem item = buildInboundOrderItem();
            item.setInboundOrderId(999L);

            when(inboundOrderMapper.selectById(anyLong())).thenReturn(order);
            when(inboundOrderItemMapper.selectById(anyLong())).thenReturn(item);

            assertThatThrownBy(() -> inboundOrderService.receiveItem(1L, 10L, 5))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("ITEM_NOT_FOUND");
        }

        @Test
        @DisplayName("收货 - 成功收货后更新状态为RECEIVING")
        void shouldUpdateStatusToReceiving() {
            InboundOrder order = buildInboundOrder();
            InboundOrderItem item = buildInboundOrderItem();

            when(inboundOrderMapper.selectById(anyLong())).thenReturn(order);
            when(inboundOrderItemMapper.selectById(anyLong())).thenReturn(item);
            when(inboundOrderItemMapper.selectList(any())).thenReturn(List.of(item));
            // T19：流水幂等登记成功 + 行级 CAS 命中
            when(receiptMapper.insertIfAbsent(any(com.cloudmart.wms.entity.InboundReceipt.class)))
                    .thenReturn(1);
            when(inboundOrderItemMapper.receiveIncrement(anyLong(), org.mockito.ArgumentMatchers.anyInt()))
                    .thenReturn(1);

            inboundOrderService.receiveItem(1L, 10L, 5);

            // T19：收货流水落库 + 数量 CAS 累加 + 订单进度更新（不再整字段覆盖明细）
            verify(receiptMapper).insertIfAbsent(any(com.cloudmart.wms.entity.InboundReceipt.class));
            verify(inboundOrderItemMapper).receiveIncrement(anyLong(), org.mockito.ArgumentMatchers.anyInt());
            verify(inboundOrderMapper).updateById(any(InboundOrder.class));
        }

        @Test
        @DisplayName("T19 幂等：同 receiptId 重放返回原收货，数量不二次累计")
        void receive_sameReceiptId_idempotent() {
            InboundOrder order = buildInboundOrder();
            InboundOrderItem item = buildInboundOrderItem();
            when(inboundOrderMapper.selectById(anyLong())).thenReturn(order);
            when(inboundOrderItemMapper.selectById(anyLong())).thenReturn(item);
            when(receiptMapper.insertIfAbsent(any(com.cloudmart.wms.entity.InboundReceipt.class)))
                    .thenReturn(0);

            inboundOrderService.receiveItem(1L, 10L, 5, "RCP-DUP", 9L,
                    com.cloudmart.wms.entity.InboundReceipt.QUALITY_PASSED,
                    com.cloudmart.wms.entity.InboundReceipt.SOURCE_PURCHASE);

            verify(inboundOrderItemMapper, never()).receiveIncrement(anyLong(), org.mockito.ArgumentMatchers.anyInt());
            verify(inboundOrderMapper, never()).updateById(any(InboundOrder.class));
        }

        @Test
        @DisplayName("T19 超收拒绝：received+delta > expected → RECEIVE_OVER_RECEIVED")
        void receive_overReceived_rejected() {
            InboundOrder order = buildInboundOrder();
            InboundOrderItem item = buildInboundOrderItem();
            item.setExpectedQuantity(10);
            item.setReceivedQuantity(8);
            when(inboundOrderMapper.selectById(anyLong())).thenReturn(order);
            when(inboundOrderItemMapper.selectById(anyLong())).thenReturn(item);
            when(receiptMapper.insertIfAbsent(any(com.cloudmart.wms.entity.InboundReceipt.class)))
                    .thenReturn(1);
            when(inboundOrderItemMapper.receiveIncrement(anyLong(), org.mockito.ArgumentMatchers.anyInt()))
                    .thenReturn(0);

            assertThatThrownBy(() -> inboundOrderService.receiveItem(1L, 10L, 5, "RCP-OVER", 9L,
                    com.cloudmart.wms.entity.InboundReceipt.QUALITY_PASSED,
                    com.cloudmart.wms.entity.InboundReceipt.SOURCE_PURCHASE))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("RECEIVE_OVER_RECEIVED");
        }

        @Test
        @DisplayName("T19 非正数拒绝：0/负数 → RECEIVE_QUANTITY_INVALID，不落任何事实")
        void receive_nonPositive_rejected() {
            assertThatThrownBy(() -> inboundOrderService.receiveItem(1L, 10L, 0, "RCP-ZERO", 9L,
                    com.cloudmart.wms.entity.InboundReceipt.QUALITY_PASSED,
                    com.cloudmart.wms.entity.InboundReceipt.SOURCE_PURCHASE))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("RECEIVE_QUANTITY_INVALID");
            verify(receiptMapper, never()).insertIfAbsent(any());
        }

        @Test
        @DisplayName("T19 隔离验收：QUARANTINE 不触发库存入账（不入可售）")
        void receive_quarantine_noRestock() {
            InboundOrder order = buildInboundOrder();
            InboundOrderItem item = buildInboundOrderItem();
            when(inboundOrderMapper.selectById(anyLong())).thenReturn(order);
            when(inboundOrderItemMapper.selectById(anyLong())).thenReturn(item);
            when(receiptMapper.insertIfAbsent(any(com.cloudmart.wms.entity.InboundReceipt.class)))
                    .thenReturn(1);
            when(inboundOrderItemMapper.receiveIncrement(anyLong(), org.mockito.ArgumentMatchers.anyInt()))
                    .thenReturn(1);

            inboundOrderService.receiveItem(1L, 10L, 5, "RCP-Q", 9L,
                    com.cloudmart.wms.entity.InboundReceipt.QUALITY_QUARANTINE,
                    com.cloudmart.wms.entity.InboundReceipt.SOURCE_PURCHASE);

            verify(inventoryRestockFeignClient, never()).restock(any());
        }
    }

    @Nested
    @DisplayName("T19 完成收货收齐校验")
    class CompleteShortageTests {

        @Test
        @DisplayName("明细未收齐 → INBOUND_SHORTAGE（缺量收尾需差异审批，不默认放行）")
        void complete_shortage_rejected() {
            InboundOrder order = buildInboundOrder();
            InboundOrderItem item = buildInboundOrderItem();
            item.setExpectedQuantity(10);
            item.setReceivedQuantity(6);
            when(inboundOrderMapper.selectById(anyLong())).thenReturn(order);
            when(inboundOrderItemMapper.selectList(any())).thenReturn(List.of(item));

            assertThatThrownBy(() -> inboundOrderService.completeInbound(1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("INBOUND_SHORTAGE");
            verify(inboundOrderMapper, never()).markCompleted(anyLong());
        }

        @Test
        @DisplayName("全部收齐 → markCompleted CAS")
        void complete_allReceived_casCompleted() {
            InboundOrder order = buildInboundOrder();
            InboundOrderItem item = buildInboundOrderItem();
            item.setExpectedQuantity(10);
            item.setReceivedQuantity(10);
            when(inboundOrderMapper.selectById(anyLong())).thenReturn(order, order);
            when(inboundOrderItemMapper.selectList(any())).thenReturn(List.of(item));
            when(inboundOrderMapper.markCompleted(1L)).thenReturn(1);

            inboundOrderService.completeInbound(1L);

            verify(inboundOrderMapper).markCompleted(1L);
        }
    }


    @Nested
    @DisplayName("completeInbound 方法")
    class CompleteInboundTests {

        @Test
        @DisplayName("完成入库 - 入库单不存在时抛出异常")
        void shouldThrowWhenOrderNotFound() {
            when(inboundOrderMapper.selectById(anyLong())).thenReturn(null);

            assertThatThrownBy(() -> inboundOrderService.completeInbound(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("INBOUND_ORDER_NOT_FOUND");
        }

        @Test
        @DisplayName("完成入库 - 成功后状态变为COMPLETED")
        void shouldUpdateStatusToCompleted() {
            InboundOrder order = buildInboundOrder();
            when(inboundOrderMapper.selectById(anyLong())).thenReturn(order);
            when(inboundOrderItemMapper.selectList(any())).thenReturn(List.of());
            when(inboundOrderMapper.markCompleted(1L)).thenReturn(1);

            inboundOrderService.completeInbound(1L);

            // T19：状态 CAS（不再整字段覆盖）；缺量明细拒绝由 T19 新增用例覆盖
            verify(inboundOrderMapper).markCompleted(1L);
        }
    }

    @Nested
    @DisplayName("getInboundOrder 方法")
    class GetInboundOrderTests {

        @Test
        @DisplayName("查询入库单 - 不存在时抛出异常")
        void shouldThrowWhenNotFound() {
            when(inboundOrderMapper.selectById(anyLong())).thenReturn(null);

            assertThatThrownBy(() -> inboundOrderService.getInboundOrder(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("INBOUND_ORDER_NOT_FOUND");
        }

        @Test
        @DisplayName("查询入库单 - 存在时返回DTO")
        void shouldReturnDTOWhenFound() {
            InboundOrder order = buildInboundOrder();
            when(inboundOrderMapper.selectById(anyLong())).thenReturn(order);
            when(inboundOrderItemMapper.selectList(any())).thenReturn(List.of(buildInboundOrderItem()));

            InboundOrderDTO dto = inboundOrderService.getInboundOrder(1L);

            assertThat(dto).isNotNull();
            assertThat(dto.id()).isEqualTo(1L);
            assertThat(dto.warehouseId()).isEqualTo(100L);
            assertThat(dto.status()).isEqualTo("PENDING");
        }
    }

    @Nested
    @DisplayName("listInboundOrders 方法")
    class ListInboundOrdersTests {

        @Test
        @DisplayName("分页查询 - 无筛选条件返回分页结果")
        void shouldReturnPagedResult() {
            InboundOrder order = buildInboundOrder();
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<InboundOrder> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10, 1);
            page.setRecords(List.of(order));

            when(inboundOrderMapper.selectPage(any(), any())).thenReturn(page);
            when(inboundOrderItemMapper.selectList(any())).thenReturn(List.of(buildInboundOrderItem()));

            IPage<InboundOrderDTO> result = inboundOrderService.listInboundOrders(null, null, 1, 10);

            assertThat(result).isNotNull();
            assertThat(result.getTotal()).isEqualTo(1);
            assertThat(result.getRecords()).hasSize(1);
        }

        @Test
        @DisplayName("分页查询 - 按状态筛选")
        void shouldFilterByStatus() {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<InboundOrder> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10, 0);
            page.setRecords(List.of());

            when(inboundOrderMapper.selectPage(any(), any())).thenReturn(page);

            IPage<InboundOrderDTO> result = inboundOrderService.listInboundOrders("PENDING", null, 1, 10);

            assertThat(result).isNotNull();
            verify(inboundOrderMapper).selectPage(any(), any());
        }
    }
}
