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

    /**
     * T03：用户可见（可领）模板列表——仅 ENABLED、未过期且仍有库存的模板。
     * 公开接口不得通过 status 参数读取后台草稿/禁用配置；全状态查询走管理接口。
     */
    List<CouponTemplateDTO> listClaimableTemplates(String type, int page, int size);

    long countClaimableTemplates(String type);

    /** T03：用户可见模板详情；不可领的模板按不存在处理（404 语义）。 */
    CouponTemplateDTO getClaimableTemplateById(Long id);

    CouponTemplateDTO disableTemplate(Long id);

    CouponTemplateDTO enableTemplate(Long id);

    UserCouponDTO claimCoupon(Long userId, Long templateId);

    List<UserCouponDTO> listUserCoupons(Long userId, String status, int page, int size);

    long countUserCoupons(Long userId, String status);

    UserCouponDTO getUserCouponById(Long id);

    /** T03：按 id + 本人 userId 查询，避免用户读取他人优惠券。 */
    UserCouponDTO getUserCouponByIdAndUser(Long id, Long userId);

    void useCoupon(Long userCouponId, Long orderId);

    /** T06：支付成功核销（RESERVED→USED；同订单幂等） */
    void confirmCoupon(Long userCouponId, Long orderId);

    void returnCoupon(Long userCouponId, Long orderId);

    int expireBatch();
}
