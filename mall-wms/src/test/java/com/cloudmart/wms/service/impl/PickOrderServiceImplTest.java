package com.cloudmart.wms.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wms.dto.CreatePickOrderRequest;
import com.cloudmart.wms.dto.PickOrderDTO;
import com.cloudmart.wms.entity.PickOrder;
import com.cloudmart.wms.entity.PickOrderItem;
import com.cloudmart.wms.repository.PickOrderItemMapper;
import com.cloudmart.wms.repository.PickOrderMapper;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PickOrderServiceImpl 单元测试")
class PickOrderServiceImplTest {

    @Mock
    private PickOrderMapper pickOrderMapper;

    @Mock
    private PickOrderItemMapper pickOrderItemMapper;

    @InjectMocks
    private PickOrderServiceImpl pickOrderService;

    @BeforeAll
    static void initTableInfo() {
        Configuration configuration = new Configuration();

        MapperBuilderAssistant pickOrderAssistant = new MapperBuilderAssistant(configuration, "");
        pickOrderAssistant.setCurrentNamespace("com.cloudmart.wms.repository.PickOrderMapper");
        TableInfoHelper.initTableInfo(pickOrderAssistant, PickOrder.class);

        MapperBuilderAssistant itemAssistant = new MapperBuilderAssistant(configuration, "");
        itemAssistant.setCurrentNamespace("com.cloudmart.wms.repository.PickOrderItemMapper");
        TableInfoHelper.initTableInfo(itemAssistant, PickOrderItem.class);
    }

    private PickOrder buildPickOrder() {
        PickOrder order = new PickOrder();
        order.setId(1L);
        order.setOrderId(100L);
        order.setWarehouseId(200L);
        order.setStatus("PENDING");
        order.setRemark("test remark");
        return order;
    }

    private PickOrderItem buildPickOrderItem() {
        PickOrderItem item = new PickOrderItem();
        item.setId(10L);
        item.setPickOrderId(1L);
        item.setSkuId(300L);
        item.setProductName("Test SKU");
        item.setSkuAttributes("颜色:红色;尺码:L");
        item.setQuantity(5);
        item.setLocationCode("B-02-03");
        item.setPickedQuantity(0);
        return item;
    }

    private CreatePickOrderRequest buildCreateRequest() {
        return new CreatePickOrderRequest(100L, 200L, "test remark");
    }

    @Nested
    @DisplayName("createPickOrder 方法")
    class CreatePickOrderTests {

        @Test
        @DisplayName("创建拣货单 - 订单已存在拣货单时抛出异常")
        void shouldThrowWhenPickOrderExists() {
            when(pickOrderMapper.selectOne(any())).thenReturn(buildPickOrder());

            assertThatThrownBy(() -> pickOrderService.createPickOrder(buildCreateRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("PICK_ORDER_EXISTS");
        }

        @Test
        @DisplayName("创建拣货单 - 成功创建")
        void shouldCreatePickOrder() {
            when(pickOrderMapper.selectOne(any())).thenReturn(null);
            when(pickOrderItemMapper.selectList(any())).thenReturn(List.of());

            pickOrderService.createPickOrder(buildCreateRequest());

            verify(pickOrderMapper).insert(any(PickOrder.class));
        }
    }

    @Nested
    @DisplayName("startPick 方法")
    class StartPickTests {

        @Test
        @DisplayName("开始拣货 - 拣货单不存在时抛出异常")
        void shouldThrowWhenNotFound() {
            when(pickOrderMapper.selectById(anyLong())).thenReturn(null);

            assertThatThrownBy(() -> pickOrderService.startPick(1L, 50L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("PICK_ORDER_NOT_FOUND");
        }

        @Test
        @DisplayName("开始拣货 - 非PENDING状态时抛出异常")
        void shouldThrowWhenStatusNotPending() {
            PickOrder order = buildPickOrder();
            order.setStatus("PICKING");
            when(pickOrderMapper.selectById(anyLong())).thenReturn(order);

            assertThatThrownBy(() -> pickOrderService.startPick(1L, 50L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("INVALID_STATUS");
        }

        @Test
        @DisplayName("开始拣货 - 成功后状态变为PICKING")
        void shouldUpdateStatusToPicking() {
            PickOrder order = buildPickOrder();
            when(pickOrderMapper.selectById(anyLong())).thenReturn(order);
            when(pickOrderItemMapper.selectList(any())).thenReturn(List.of());

            pickOrderService.startPick(1L, 50L);

            verify(pickOrderMapper).updateById(any(PickOrder.class));
        }
    }

    @Nested
    @DisplayName("confirmPicked 方法")
    class ConfirmPickedTests {

        @Test
        @DisplayName("确认拣货 - 拣货单不存在时抛出异常")
        void shouldThrowWhenNotFound() {
            when(pickOrderMapper.selectById(anyLong())).thenReturn(null);

            assertThatThrownBy(() -> pickOrderService.confirmPicked(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("PICK_ORDER_NOT_FOUND");
        }

        @Test
        @DisplayName("确认拣货 - 非PICKING状态时抛出异常")
        void shouldThrowWhenStatusNotPicking() {
            PickOrder order = buildPickOrder();
            order.setStatus("PENDING");
            when(pickOrderMapper.selectById(anyLong())).thenReturn(order);

            assertThatThrownBy(() -> pickOrderService.confirmPicked(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("INVALID_STATUS");
        }

        @Test
        @DisplayName("确认拣货 - 成功后状态变为PICKED")
        void shouldUpdateStatusToPicked() {
            PickOrder order = buildPickOrder();
            order.setStatus("PICKING");
            when(pickOrderMapper.selectById(anyLong())).thenReturn(order);
            when(pickOrderItemMapper.selectList(any())).thenReturn(List.of());

            pickOrderService.confirmPicked(1L);

            verify(pickOrderMapper).updateById(any(PickOrder.class));
        }
    }

    @Nested
    @DisplayName("confirmPacked 方法")
    class ConfirmPackedTests {

        @Test
        @DisplayName("确认打包 - 拣货单不存在时抛出异常")
        void shouldThrowWhenNotFound() {
            when(pickOrderMapper.selectById(anyLong())).thenReturn(null);

            assertThatThrownBy(() -> pickOrderService.confirmPacked(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("PICK_ORDER_NOT_FOUND");
        }

        @Test
        @DisplayName("确认打包 - 非PICKED状态时抛出异常")
        void shouldThrowWhenStatusNotPicked() {
            PickOrder order = buildPickOrder();
            order.setStatus("PICKING");
            when(pickOrderMapper.selectById(anyLong())).thenReturn(order);

            assertThatThrownBy(() -> pickOrderService.confirmPacked(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("INVALID_STATUS");
        }

        @Test
        @DisplayName("确认打包 - 成功后状态变为PACKED")
        void shouldUpdateStatusToPacked() {
            PickOrder order = buildPickOrder();
            order.setStatus("PICKED");
            when(pickOrderMapper.selectById(anyLong())).thenReturn(order);
            when(pickOrderItemMapper.selectList(any())).thenReturn(List.of());

            pickOrderService.confirmPacked(1L);

            verify(pickOrderMapper).updateById(any(PickOrder.class));
        }
    }

    @Nested
    @DisplayName("getPickOrder 方法")
    class GetPickOrderTests {

        @Test
        @DisplayName("查询拣货单 - 不存在时抛出异常")
        void shouldThrowWhenNotFound() {
            when(pickOrderMapper.selectById(anyLong())).thenReturn(null);

            assertThatThrownBy(() -> pickOrderService.getPickOrder(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("PICK_ORDER_NOT_FOUND");
        }

        @Test
        @DisplayName("查询拣货单 - 存在时返回DTO")
        void shouldReturnDTOWhenFound() {
            PickOrder order = buildPickOrder();
            when(pickOrderMapper.selectById(anyLong())).thenReturn(order);
            when(pickOrderItemMapper.selectList(any())).thenReturn(List.of(buildPickOrderItem()));

            PickOrderDTO dto = pickOrderService.getPickOrder(1L);

            assertThat(dto).isNotNull();
            assertThat(dto.id()).isEqualTo(1L);
            assertThat(dto.orderId()).isEqualTo(100L);
            assertThat(dto.status()).isEqualTo("PENDING");
        }
    }

    @Nested
    @DisplayName("listPickOrders 方法")
    class ListPickOrdersTests {

        @Test
        @DisplayName("分页查询 - 返回分页结果")
        void shouldReturnPagedResult() {
            PickOrder order = buildPickOrder();
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<PickOrder> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10, 1);
            page.setRecords(List.of(order));

            when(pickOrderMapper.selectPage(any(), any())).thenReturn(page);
            when(pickOrderItemMapper.selectList(any())).thenReturn(List.of(buildPickOrderItem()));

            IPage<PickOrderDTO> result = pickOrderService.listPickOrders(null, null, 1, 10);

            assertThat(result).isNotNull();
            assertThat(result.getTotal()).isEqualTo(1);
            assertThat(result.getRecords()).hasSize(1);
        }

        @Test
        @DisplayName("分页查询 - 按仓库ID筛选")
        void shouldFilterByWarehouseId() {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<PickOrder> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10, 0);
            page.setRecords(List.of());

            when(pickOrderMapper.selectPage(any(), any())).thenReturn(page);

            IPage<PickOrderDTO> result = pickOrderService.listPickOrders(null, 200L, 1, 10);

            assertThat(result).isNotNull();
            verify(pickOrderMapper).selectPage(any(), any());
        }
    }

    @Nested
    @DisplayName("findByOrderId 方法")
    class FindByOrderIdTests {

        @Test
        @DisplayName("按订单ID查询 - 不存在时返回null")
        void shouldReturnNullWhenNotFound() {
            when(pickOrderMapper.selectOne(any())).thenReturn(null);

            PickOrderDTO result = pickOrderService.findByOrderId(999L);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("按订单ID查询 - 存在时返回DTO")
        void shouldReturnDTOWhenFound() {
            PickOrder order = buildPickOrder();
            when(pickOrderMapper.selectOne(any())).thenReturn(order);
            when(pickOrderItemMapper.selectList(any())).thenReturn(List.of(buildPickOrderItem()));

            PickOrderDTO dto = pickOrderService.findByOrderId(100L);

            assertThat(dto).isNotNull();
            assertThat(dto.orderId()).isEqualTo(100L);
        }
    }
}
