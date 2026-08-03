package com.cloudmart.product.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.product.dto.WishlistDTO;

public interface WishlistService {

    void addToList(Long userId, Long productId);

    void removeFromList(Long userId, Long productId);

    Page<WishlistDTO> getUserWishlist(Long userId, int page, int size);

    boolean isInWishlist(Long userId, Long productId);
}
