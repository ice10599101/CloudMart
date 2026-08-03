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
}
