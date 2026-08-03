package com.cloudmart.inventory.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.inventory.entity.Inventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface InventoryMapper extends BaseMapper<Inventory> {

    @Update("UPDATE inventory SET available = available - #{quantity}, reserved = reserved + #{quantity}, updated_at = NOW() " +
            "WHERE sku_id = #{skuId} AND available >= #{quantity}")
    int deductStock(@Param("skuId") Long skuId, @Param("quantity") Integer quantity);

    @Update("UPDATE inventory SET available = available + #{quantity}, reserved = reserved - #{quantity}, updated_at = NOW() " +
            "WHERE sku_id = #{skuId}")
    int releaseStock(@Param("skuId") Long skuId, @Param("quantity") Integer quantity);

    @Update("UPDATE inventory SET reserved = reserved - #{quantity}, updated_at = NOW() " +
            "WHERE sku_id = #{skuId} AND reserved >= #{quantity}")
    int confirmDeduct(@Param("skuId") Long skuId, @Param("quantity") Integer quantity);
}
