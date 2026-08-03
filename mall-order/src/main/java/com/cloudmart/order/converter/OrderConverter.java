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

@Mapper(componentModel = "spring")
public interface OrderConverter {

    @Mapping(target = "items", source = "items")
    OrderDTO toDTO(Order order, List<OrderItemDTO> items);

    OrderItemDTO toItemDTO(OrderItem orderItem);

    List<OrderItemDTO> toItemDTOList(List<OrderItem> orderItems);

    @Mapping(target = "items", source = "items")
    OrderVO toVO(Order order, List<OrderItemVO> items);

    OrderItemVO toItemVO(OrderItem orderItem);

    List<OrderItemVO> toItemVOList(List<OrderItem> orderItems);

    @Mapping(target = "freightAmount", ignore = true)
    @Mapping(target = "paidAt", ignore = true)
    @Mapping(source = "items", target = "items")
    OrderVO orderDtoToVO(OrderDTO dto);

    @Mapping(source = "skuImage", target = "productImage")
    @Mapping(target = "subtotal", ignore = true)
    OrderItemVO orderItemDtoToVO(OrderItemDTO dto);

    default List<OrderItemVO> orderItemDtoListToVOList(List<OrderItemDTO> dtos) {
        return dtos.stream().map(dto -> {
            OrderItemVO base = orderItemDtoToVO(dto);
            if (base.subtotal() == null && base.price() != null && base.quantity() != null) {
                return new OrderItemVO(base.id(), base.productId(), base.productName(),
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
