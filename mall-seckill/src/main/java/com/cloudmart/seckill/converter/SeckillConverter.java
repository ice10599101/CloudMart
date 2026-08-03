package com.cloudmart.seckill.converter;

import com.cloudmart.seckill.dto.SeckillActivityDTO;
import com.cloudmart.seckill.dto.SeckillProductDTO;
import com.cloudmart.seckill.dto.SeckillResultDTO;
import com.cloudmart.seckill.entity.SeckillActivity;
import com.cloudmart.seckill.entity.SeckillProduct;
import com.cloudmart.seckill.vo.SeckillActivityVO;
import com.cloudmart.seckill.vo.SeckillProductVO;
import com.cloudmart.seckill.vo.SeckillResultVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SeckillConverter {

    SeckillActivityDTO toActivityDTO(SeckillActivity entity);

    List<SeckillActivityDTO> toActivityDTOList(List<SeckillActivity> entities);

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "status", constant = "DISABLED")
    SeckillActivity toEntity(com.cloudmart.seckill.dto.CreateActivityRequest request);

    SeckillProductDTO toProductDTO(SeckillProduct entity);

    List<SeckillProductDTO> toProductDTOList(List<SeckillProduct> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "activityId", ignore = true)
    @Mapping(target = "availableStock", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    SeckillProduct toEntity(com.cloudmart.seckill.dto.AddSeckillProductRequest request);

    SeckillActivityVO toActivityVO(SeckillActivity entity);

    List<SeckillActivityVO> toActivityVOList(List<SeckillActivity> entities);

    @Mapping(target = "productId", ignore = true)
    @Mapping(target = "productName", ignore = true)
    @Mapping(target = "productImage", ignore = true)
    @Mapping(target = "limitPerUser", source = "perUserLimit")
    SeckillProductVO toProductVO(SeckillProduct entity);

    List<SeckillProductVO> toProductVOList(List<SeckillProduct> entities);

    SeckillActivityVO activityDtoToVO(SeckillActivityDTO dto);

    default List<SeckillActivityVO> activityDtoListToVOList(List<SeckillActivityDTO> dtos) {
        return dtos.stream().map(this::activityDtoToVO).toList();
    }

    @Mapping(target = "productId", ignore = true)
    @Mapping(target = "productName", ignore = true)
    @Mapping(target = "productImage", ignore = true)
    @Mapping(target = "limitPerUser", source = "perUserLimit")
    SeckillProductVO productDtoToVO(SeckillProductDTO dto);

    default List<SeckillProductVO> productDtoListToVOList(List<SeckillProductDTO> dtos) {
        return dtos.stream().map(this::productDtoToVO).toList();
    }

    default SeckillResultVO resultDtoToVO(SeckillResultDTO dto) {
        boolean success = "SUCCESS".equals(dto.status());
        String orderNo = dto.orderId() != null ? String.valueOf(dto.orderId()) : null;
        return new SeckillResultVO(success, orderNo, dto.message());
    }
}
