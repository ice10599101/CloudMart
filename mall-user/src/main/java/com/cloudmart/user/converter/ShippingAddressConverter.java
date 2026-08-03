package com.cloudmart.user.converter;

import com.cloudmart.user.dto.CreateAddressRequest;
import com.cloudmart.user.dto.ShippingAddressDTO;
import com.cloudmart.user.dto.UpdateAddressRequest;
import com.cloudmart.user.entity.ShippingAddress;
import com.cloudmart.user.vo.ShippingAddressVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ShippingAddressConverter {

    @Mapping(target = "isDefault", expression = "java(entity.getIsDefault() != null && entity.getIsDefault() == 1)")
    ShippingAddressDTO toDTO(ShippingAddress entity);

    List<ShippingAddressDTO> toDTOList(List<ShippingAddress> entities);

    @Mapping(target = "isDefault", expression = "java(entity.getIsDefault() != null && entity.getIsDefault() == 1)")
    ShippingAddressVO toVO(ShippingAddress entity);

    List<ShippingAddressVO> toVOList(List<ShippingAddress> entities);

    @Mapping(target = "isDefault", expression = "java(request.isDefault() != null && request.isDefault() ? 1 : 0)")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ShippingAddress toEntity(CreateAddressRequest request);

    @Mapping(target = "isDefault", expression = "java(request.isDefault() != null && request.isDefault() ? 1 : 0)")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ShippingAddress toEntity(UpdateAddressRequest request);
}
