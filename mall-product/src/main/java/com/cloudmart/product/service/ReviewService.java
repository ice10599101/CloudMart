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

    void updateReviewStatus(Long id, Integer status);

    void deleteReview(Long id);
}
