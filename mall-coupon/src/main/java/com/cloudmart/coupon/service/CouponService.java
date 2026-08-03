package com.cloudmart.coupon.service;

import com.cloudmart.coupon.dto.CreateCouponTemplateRequest;
import com.cloudmart.coupon.dto.CouponTemplateDTO;
import com.cloudmart.coupon.dto.UserCouponDTO;

import java.util.List;

public interface CouponService {

    CouponTemplateDTO createTemplate(CreateCouponTemplateRequest request);

    CouponTemplateDTO getTemplateById(Long id);

    List<CouponTemplateDTO> listTemplates(String type, String status, int page, int size);

    long countTemplates(String type, String status);

    CouponTemplateDTO disableTemplate(Long id);

    CouponTemplateDTO enableTemplate(Long id);

    UserCouponDTO claimCoupon(Long userId, Long templateId);

    List<UserCouponDTO> listUserCoupons(Long userId, String status, int page, int size);

    long countUserCoupons(Long userId, String status);

    UserCouponDTO getUserCouponById(Long id);

    void useCoupon(Long userCouponId, Long orderId);

    void returnCoupon(Long userCouponId, Long orderId);

    int expireBatch();
}
