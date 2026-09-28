package com.cloudmart.inventory.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.inventory.entity.InventoryReservation;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 库存预占台账 Mapper（STOCK-01）：状态迁移全部为条件更新——
 * RESERVED → CONFIRMED / RELEASED 只能成功一次，重复操作返回 0 行。
 */
@Mapper
public interface InventoryReservationMapper extends BaseMapper<InventoryReservation> {

    /** 幂等插入：UNIQUE(order_id, sku_id) 冲突返回 0 行 */
    @Insert("INSERT IGNORE INTO inventory_reservation (order_id, sku_id, quantity, status) "
            + "VALUES (#{orderId}, #{skuId}, #{quantity}, 'RESERVED')")
    int insertReservation(@Param("orderId") Long orderId, @Param("skuId") Long skuId,
                          @Param("quantity") Integer quantity);

    /** RESERVED → CONFIRMED（一次性）：0 行表示已确认/已释放/不存在 */
    @Update("UPDATE inventory_reservation SET status = 'CONFIRMED', confirmed_at = NOW(3), "
            + "version = version + 1 WHERE order_id = #{orderId} AND sku_id = #{skuId} AND status = 'RESERVED'")
    int confirmReservation(@Param("orderId") Long orderId, @Param("skuId") Long skuId);

    /** RESERVED → RELEASED（一次性）：0 行表示已确认/已释放/不存在 */
    @Update("UPDATE inventory_reservation SET status = 'RELEASED', released_at = NOW(3), "
            + "version = version + 1 WHERE order_id = #{orderId} AND sku_id = #{skuId} AND status = 'RESERVED'")
    int releaseReservation(@Param("orderId") Long orderId, @Param("skuId") Long skuId);

    @Select("SELECT * FROM inventory_reservation WHERE order_id = #{orderId} AND sku_id = #{skuId}")
    InventoryReservation findByOrderAndSku(@Param("orderId") Long orderId, @Param("skuId") Long skuId);

    @Select("SELECT * FROM inventory_reservation WHERE order_id = #{orderId} AND status = 'RESERVED'")
    List<InventoryReservation> findReservedByOrder(@Param("orderId") Long orderId);
}
