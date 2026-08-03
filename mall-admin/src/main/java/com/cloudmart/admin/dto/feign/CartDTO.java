package com.cloudmart.admin.dto.feign;

import java.math.BigDecimal;
import java.util.List;

/**
 * 购物车 Feign 传输对象，与 mall-cart 服务端 CartDTO 字段对齐
 */
public record CartDTO(
    List<CartItemDTO> items,
    Integer totalQuantity,
    BigDecimal totalPrice
) {}
