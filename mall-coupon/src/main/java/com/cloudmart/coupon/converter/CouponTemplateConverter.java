package com.cloudmart.coupon.converter;

import com.cloudmart.coupon.dto.CouponTemplateDTO;
import com.cloudmart.coupon.entity.CouponTemplate;
import com.cloudmart.coupon.vo.CouponTemplateVO;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CouponTemplateConverter {

    CouponTemplateDTO toDTO(CouponTemplate template);

    List<CouponTemplateDTO> toDTOList(List<CouponTemplate> templates);

    CouponTemplateVO toVO(CouponTemplate template);

    List<CouponTemplateVO> toVOList(List<CouponTemplate> templates);

    CouponTemplateVO dtoToVO(CouponTemplateDTO dto);

    default List<CouponTemplateVO> dtoListToVOList(List<CouponTemplateDTO> dtos) {
        return dtos.stream().map(this::dtoToVO).toList();
    }
}
