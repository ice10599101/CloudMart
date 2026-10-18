package com.cloudmart.order.converter;

import com.cloudmart.order.dto.OrderDTO;
import com.cloudmart.order.vo.OrderVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** T07：VO 必须携带服务端计算的 allowedActions。 */
@DisplayName("T07 订单 VO 动作映射")
class OrderConverterTest {

    private final OrderConverter converter = Mappers.getMapper(OrderConverter.class);

    private OrderDTO dto(String status) {
        return new OrderDTO(1L, "ORD001", new BigDecimal("100.00"), new BigDecimal("100.00"),
                BigDecimal.ZERO, null, status, "张三", "13800138000", "北京市",
                null, null, null, null, List.of(), null, null, "NONE", BigDecimal.ZERO);
    }

    @Test
    @DisplayName("待付款 VO 带 PAY/CANCEL")
    void pendingPaymentActions() {
        OrderVO vo = converter.orderDtoToVO(dto("PENDING_PAYMENT"));
        assertThat(vo.allowedActions()).containsExactly("PAY", "CANCEL");
    }

    @Test
    @DisplayName("已发货 VO 带 CONFIRM_RECEIPT")
    void shippedActions() {
        OrderVO vo = converter.orderDtoToVO(dto("SHIPPED"));
        assertThat(vo.allowedActions()).containsExactly("CONFIRM_RECEIPT");
    }

    @Test
    @DisplayName("已取消 VO 无动作")
    void cancelledActions() {
        OrderVO vo = converter.orderDtoToVO(dto("CANCELLED"));
        assertThat(vo.allowedActions()).isEmpty();
    }
}
