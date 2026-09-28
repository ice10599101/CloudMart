package com.cloudmart.wms.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.wms.entity.ShippingOrder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ShippingOrderMapper extends BaseMapper<ShippingOrder> {

    /** WMS-01：状态机条件更新——SHIPPED 时同步写运单号与出库时间 */
    @org.apache.ibatis.annotations.Update(
            "UPDATE shipping_orders SET status = #{target}, shipped_at = NOW(3), updated_at = NOW() "
            + "WHERE id = #{id} AND status = #{expected}")
    int updateStatusIfMatch(@org.apache.ibatis.annotations.Param("id") Long id,
                            @org.apache.ibatis.annotations.Param("expected") String expected,
                            @org.apache.ibatis.annotations.Param("target") String target);

    @org.apache.ibatis.annotations.Update(
            "UPDATE shipping_orders SET status = #{target}, updated_at = NOW() "
            + "WHERE id = #{id} AND status = #{expected}")
    int updateStatusPlainIfMatch(@org.apache.ibatis.annotations.Param("id") Long id,
                                 @org.apache.ibatis.annotations.Param("expected") String expected,
                                 @org.apache.ibatis.annotations.Param("target") String target);

    @org.apache.ibatis.annotations.Select(
            "SELECT COUNT(*) FROM shipping_orders WHERE order_id = #{orderId}")
    long countByOrderId(@org.apache.ibatis.annotations.Param("orderId") Long orderId);
}
