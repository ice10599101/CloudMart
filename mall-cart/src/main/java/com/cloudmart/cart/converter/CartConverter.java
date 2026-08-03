package com.cloudmart.cart.converter;

import com.cloudmart.cart.dto.CartDTO;
import com.cloudmart.cart.dto.CartItemDTO;
import com.cloudmart.cart.entity.CartItem;
import com.cloudmart.cart.vo.CartItemVO;
import com.cloudmart.cart.vo.CartVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CartConverter {

    CartItemDTO toDTO(CartItem item);

    @Mapping(target = "selected", expression = "java(item.getChecked() != null && item.getChecked() == 1)")
    @Mapping(target = "productName", ignore = true)
    @Mapping(target = "productImage", ignore = true)
    @Mapping(target = "skuAttributes", ignore = true)
    @Mapping(target = "price", ignore = true)
    @Mapping(target = "subtotal", ignore = true)
    CartItemVO toVO(CartItem item);

    @Mapping(target = "selected", expression = "java(dto.checked() != null && dto.checked() == 1)")
    @Mapping(target = "productImage", source = "skuImage")
    @Mapping(target = "subtotal", ignore = true)
    CartItemVO cartItemDtoToVO(CartItemDTO dto);

    default List<CartItemVO> cartItemDtoListToVOList(List<CartItemDTO> dtos) {
        return dtos.stream().map(dto -> {
            CartItemVO base = cartItemDtoToVO(dto);
            if (base.subtotal() == null && base.price() != null && base.quantity() != null) {
                return new CartItemVO(base.id(), base.productId(), base.productName(),
                        base.productImage(), base.skuId(), base.skuAttributes(),
                        base.price(), base.quantity(),
                        base.price().multiply(java.math.BigDecimal.valueOf(base.quantity())),
                        base.selected());
            }
            return base;
        }).toList();
    }

    @Mapping(target = "totalCount", source = "totalQuantity")
    @Mapping(target = "totalAmount", source = "totalPrice")
    CartVO cartDtoToVO(CartDTO dto);

    default CartVO cartDtoToVOWithItems(CartDTO dto) {
        CartVO base = cartDtoToVO(dto);
        List<CartItemVO> itemVOs = dto.items() != null
                ? cartItemDtoListToVOList(dto.items()) : List.of();
        return new CartVO(itemVOs, base.totalCount(), base.totalAmount());
    }
}
