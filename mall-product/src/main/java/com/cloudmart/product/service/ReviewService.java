package com.cloudmart.product.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.product.dto.CreateReviewRequest;
import com.cloudmart.product.dto.ReviewDTO;
import com.cloudmart.product.dto.ReviewStatsDTO;

public interface ReviewService {

    ReviewDTO createReview(Long userId, CreateReviewRequest request);

    Page<ReviewDTO> getProductReviews(Long productId, int page, int size);

    ReviewStatsDTO getReviewStats(Long productId);

    boolean hasUserReviewedProduct(Long userId, Long orderId, Long productId);

    Page<ReviewDTO> listReviewsForAdmin(Long productId, Integer status, int page, int size);

    ReviewDTO getReviewById(Long id);

    /** R08：作者按订单行查自己的评价（含下架理由）；未评价返回 null */
    ReviewDTO getOwnReviewByOrderItem(Long userId, Long orderItemId);

    void updateReviewStatus(Long id, Integer status);

    /** R08：管理端审核/下架（下架必填理由，作者可见） */
    void updateReviewStatus(Long id, Integer status, String reason);

    void deleteReview(Long id);
}
