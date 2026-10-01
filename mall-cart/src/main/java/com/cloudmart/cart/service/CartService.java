package com.cloudmart.cart.service;

import com.cloudmart.cart.dto.AddCartItemRequest;
import com.cloudmart.cart.dto.CartDTO;
import com.cloudmart.cart.dto.CartItemDTO;
import com.cloudmart.cart.dto.UpdateCartItemRequest;

public interface CartService {

    CartDTO getCart(Long userId);

    CartItemDTO addItem(Long userId, AddCartItemRequest request);

    CartItemDTO updateItem(Long userId, Long skuId, UpdateCartItemRequest request);

    void removeItem(Long userId, Long skuId);

    void clearCart(Long userId);

    void clearCheckedItems(Long userId);

    /** T03/TRADE-02：精确清理本次购买行（按订单实购 skuId 集合），不影响其他勾选商品 */
    void clearCheckedBySkus(Long userId, java.util.List<Long> skuIds);

    void syncToDatabase(Long userId);
}
