package com.cloudmart.order.converter;

import com.cloudmart.order.dto.OrderDTO;
import com.cloudmart.order.dto.OrderItemDTO;
import com.cloudmart.order.entity.Order;
import com.cloudmart.order.entity.OrderItem;
import com.cloudmart.order.vo.OrderItemVO;
import com.cloudmart.order.vo.OrderVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = org.mapstruct.ReportingPolicy.ERROR)
public interface OrderConverter {

    @Mapping(target = "items", source = "items")
    // R11：是否需要收费渠道支付——应付 > 0 才为 true（零元单建单即内部结算完成）
    @Mapping(target = "paymentRequired",
            expression = "java(order.getPayAmount() != null && order.getPayAmount().compareTo(java.math.BigDecimal.ZERO) > 0)")
    // R12：优惠明细快照 JSON → 结构化列表（供展示/售后/反查）
    @Mapping(target = "discountLines",
            expression = "java(com.cloudmart.order.dto.DiscountLine.parseJson(order.getDiscountLines()))")
    OrderDTO toDTO(Order order, List<OrderItemDTO> items);

    OrderItemDTO toItemDTO(OrderItem orderItem);

    List<OrderItemDTO> toItemDTOList(List<OrderItem> orderItems);

    @Mapping(target = "items", source = "items")
    @Mapping(target = "discountLines",
            expression = "java(com.cloudmart.order.dto.DiscountLine.parseJson(order.getDiscountLines()))")
    OrderVO toVO(Order order, List<OrderItemVO> items);

    @Mapping(source = "skuImage", target = "productImage")
    @Mapping(target = "subtotal", ignore = true) // default 方法按 price*quantity 补齐
    OrderItemVO toItemVO(OrderItem orderItem);

    List<OrderItemVO> toItemVOList(List<OrderItem> orderItems);

    @Mapping(source = "items", target = "items")
    OrderVO orderDtoToVO(OrderDTO dto);

    @Mapping(source = "skuImage", target = "productImage")
    @Mapping(target = "subtotal", ignore = true)
    OrderItemVO orderItemDtoToVO(OrderItemDTO dto);

    default List<OrderItemVO> orderItemDtoListToVOList(List<OrderItemDTO> dtos) {
        return dtos.stream().map(dto -> {
            OrderItemVO base = orderItemDtoToVO(dto);
            if (base.subtotal() == null && base.price() != null && base.quantity() != null) {
                return new OrderItemVO(base.id(), base.productId(), base.skuId(), base.productName(),
                        base.productImage(), base.skuAttributes(), base.price(),
                        base.quantity(), base.price().multiply(java.math.BigDecimal.valueOf(base.quantity())));
            }
            return base;
        }).toList();
    }

    default List<OrderVO> orderDtoToVOList(List<OrderDTO> dtos) {
        return dtos.stream().map(this::orderDtoToVO).toList();
    }
}
