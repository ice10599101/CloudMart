package com.cloudmart.wms.converter;

import com.cloudmart.wms.dto.InboundOrderDTO;
import com.cloudmart.wms.dto.PickOrderDTO;
import com.cloudmart.wms.dto.ShippingOrderDTO;
import com.cloudmart.wms.entity.InboundOrder;
import com.cloudmart.wms.entity.PickOrder;
import com.cloudmart.wms.entity.ShippingOrder;
import com.cloudmart.wms.entity.Warehouse;
import com.cloudmart.wms.vo.InboundOrderVO;
import com.cloudmart.wms.vo.PickOrderVO;
import com.cloudmart.wms.vo.ShippingOrderVO;
import com.cloudmart.wms.vo.WarehouseVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface WmsConverter {

    @Mapping(target = "code", ignore = true)
    @Mapping(target = "contactPhone", source = "contactPhone")
    WarehouseVO toWarehouseVO(Warehouse entity);

    List<WarehouseVO> toWarehouseVOList(List<Warehouse> entities);

    @Mapping(target = "warehouseName", ignore = true)
    PickOrderVO toPickOrderVO(PickOrder entity);

    List<PickOrderVO> toPickOrderVOList(List<PickOrder> entities);

    @Mapping(target = "warehouseName", ignore = true)
    @Mapping(target = "supplierName", ignore = true)
    InboundOrderVO toInboundOrderVO(InboundOrder entity);

    List<InboundOrderVO> toInboundOrderVOList(List<InboundOrder> entities);

    @Mapping(target = "trackingNo", source = "shippingNo")
    @Mapping(target = "shippedAt", ignore = true)
    ShippingOrderVO toShippingOrderVO(ShippingOrder entity);

    List<ShippingOrderVO> toShippingOrderVOList(List<ShippingOrder> entities);

    @Mapping(target = "warehouseName", ignore = true)
    PickOrderVO fromPickOrderDTO(PickOrderDTO dto);

    @Mapping(target = "warehouseName", ignore = true)
    @Mapping(target = "supplierName", ignore = true)
    InboundOrderVO fromInboundOrderDTO(InboundOrderDTO dto);

    @Mapping(target = "trackingNo", source = "shippingNo")
    @Mapping(target = "shippedAt", source = "createdAt")
    ShippingOrderVO fromShippingOrderDTO(ShippingOrderDTO dto);
}
