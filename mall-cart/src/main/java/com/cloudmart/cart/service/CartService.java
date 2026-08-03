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

    void syncToDatabase(Long userId);
}
