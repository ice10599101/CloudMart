package com.cloudmart.marketing.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cloudmart.marketing.dto.*;

public interface TieredPromotionService {

    TieredPromotionDTO createPromotion(CreateTieredPromotionRequest request);

    TieredPromotionDTO enablePromotion(Long id);

    TieredPromotionDTO disablePromotion(Long id);

    TieredPromotionDTO getPromotion(Long id);

    IPage<TieredPromotionDTO> listPromotions(String status, int page, int size);

    CalculateDiscountResult calculateDiscount(CalculateDiscountRequest request);

    /**
     * R12：购物篮自动匹配当前生效（ENABLED 且在时间窗内）的最优满减活动；
     * 无任何命中返回 matched=false 的空结果（不抛异常）。
     */
    TieredMatchResult matchBest(TieredMatchRequest request);

    /**
     * R12：管理端发布前预览——忽略活动状态/时间窗，纯按规则计算指定购物篮金额
     * 在该活动下的命中结果（DISABLED/未开始活动同样可预览）。
     */
    CalculateDiscountResult previewDiscount(Long promotionId, java.math.BigDecimal orderAmount);
}
