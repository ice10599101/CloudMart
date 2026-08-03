package com.cloudmart.coupon.converter;

import com.cloudmart.coupon.dto.UserCouponDTO;
import com.cloudmart.coupon.entity.CouponTemplate;
import com.cloudmart.coupon.entity.UserCoupon;
import com.cloudmart.coupon.vo.UserCouponVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserCouponConverter {

    @Mapping(target = "id", source = "userCoupon.id")
    @Mapping(target = "userId", source = "userCoupon.userId")
    @Mapping(target = "templateId", source = "userCoupon.templateId")
    @Mapping(target = "status", source = "userCoupon.status")
    @Mapping(target = "orderId", source = "userCoupon.orderId")
    @Mapping(target = "receivedAt", source = "userCoupon.receivedAt")
    @Mapping(target = "usedAt", source = "userCoupon.usedAt")
    @Mapping(target = "expiredAt", source = "userCoupon.expiredAt")
    @Mapping(target = "templateName", source = "template.name")
    @Mapping(target = "templateType", source = "template.type")
    @Mapping(target = "thresholdAmount", source = "template.thresholdAmount")
    @Mapping(target = "discountAmount", source = "template.discountAmount")
    @Mapping(target = "discountRate", source = "template.discountRate")
    UserCouponDTO toDTO(UserCoupon userCoupon, CouponTemplate template);

    @Mapping(target = "templateName", ignore = true)
    @Mapping(target = "templateType", ignore = true)
    @Mapping(target = "thresholdAmount", ignore = true)
    @Mapping(target = "discountAmount", ignore = true)
    @Mapping(target = "discountRate", ignore = true)
    UserCouponDTO toDTO(UserCoupon userCoupon);

    @Mapping(target = "id", source = "userCoupon.id")
    @Mapping(target = "status", source = "userCoupon.status")
    @Mapping(target = "couponTemplateId", source = "userCoupon.templateId")
    @Mapping(target = "couponName", source = "template.name")
    @Mapping(target = "couponType", source = "template.type")
    @Mapping(target = "discountValue", source = "template.discountAmount")
    @Mapping(target = "minOrderAmount", source = "template.thresholdAmount")
    UserCouponVO toVO(UserCoupon userCoupon, CouponTemplate template);

    @Mapping(target = "couponTemplateId", source = "templateId")
    @Mapping(target = "couponName", ignore = true)
    @Mapping(target = "couponType", ignore = true)
    @Mapping(target = "discountValue", ignore = true)
    @Mapping(target = "minOrderAmount", ignore = true)
    UserCouponVO toVO(UserCoupon userCoupon);

    @Mapping(target = "couponTemplateId", source = "templateId")
    @Mapping(target = "couponName", source = "templateName")
    @Mapping(target = "couponType", source = "templateType")
    @Mapping(target = "discountValue", source = "discountAmount")
    @Mapping(target = "minOrderAmount", source = "thresholdAmount")
    UserCouponVO dtoToVO(UserCouponDTO dto);
}
