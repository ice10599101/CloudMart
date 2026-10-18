package com.cloudmart.wms.converter;

import com.cloudmart.wms.dto.InboundOrderDTO;
import com.cloudmart.wms.dto.PickOrderDTO;
import com.cloudmart.wms.dto.ShippingOrderDTO;
import com.cloudmart.wms.entity.Warehouse;
import com.cloudmart.wms.vo.InboundOrderDetailVO;
import com.cloudmart.wms.vo.InboundOrderVO;
import com.cloudmart.wms.vo.PickOrderDetailVO;
import com.cloudmart.wms.vo.PickOrderVO;
import com.cloudmart.wms.vo.ShippingOrderVO;
import com.cloudmart.wms.vo.WarehouseVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * WMS 转换器（TRADE-03：列表 VO 与详情 VO 分离；DTO→VO 全字段直映，不再静默丢弃）。
 */
@Mapper(componentModel = "spring")
public interface WmsConverter {

    WarehouseVO toWarehouseVO(Warehouse entity);

    List<WarehouseVO> toWarehouseVOList(List<Warehouse> entities);

    // ---- 拣货单：列表（薄）/ 详情（含行项与人员分配） ----

    @Mapping(target = "warehouseName", ignore = true)
    PickOrderVO toPickOrderVO(PickOrderDTO dto);

    @Mapping(target = "warehouseName", ignore = true)
    PickOrderDetailVO toPickOrderDetailVO(PickOrderDTO dto);

    // ---- 入库单：列表（薄）/ 详情（含逐项收货进度） ----

    @Mapping(target = "warehouseName", ignore = true)
    InboundOrderVO toInboundOrderVO(InboundOrderDTO dto);

    @Mapping(target = "warehouseName", ignore = true)
    InboundOrderDetailVO toInboundOrderDetailVO(InboundOrderDTO dto);

    // ---- 物流：内部出库单号与承运商运单号分列（修复旧映射把 shippingNo 冒充 trackingNo） ----

    ShippingOrderVO fromShippingOrderDTO(ShippingOrderDTO dto);

    /** T05：轨迹时间线（详情页独立查询，附带订单归属校验）。 */
    com.cloudmart.wms.vo.ShippingTrackingVO toTrackingVO(
            com.cloudmart.wms.dto.ShippingTrackingDTO dto);

    List<com.cloudmart.wms.vo.ShippingTrackingVO> toTrackingVOList(
            List<com.cloudmart.wms.dto.ShippingTrackingDTO> dtos);
}
