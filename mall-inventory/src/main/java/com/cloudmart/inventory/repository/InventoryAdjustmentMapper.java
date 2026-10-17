package com.cloudmart.inventory.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.inventory.entity.InventoryAdjustment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface InventoryAdjustmentMapper extends BaseMapper<InventoryAdjustment> {

    /**
     * TRADE-02 调整 CAS：版本一致且调整后可用库存不为负才生效（预占只读，不参与调整）。
     * 返回 0 行 = 版本冲突或库存不足，由调用方读当前行区分诊断。
     */
    @Update("UPDATE inventory SET available = available + #{delta}, version = version + 1 "
            + "WHERE sku_id = #{skuId} AND version = #{expectedVersion} AND available + #{delta} >= 0")
    int casAdjust(@Param("skuId") Long skuId,
                  @Param("delta") int delta,
                  @Param("expectedVersion") int expectedVersion);

    /** 幂等查询：同 request_key 的既有调整单 */
    @Select("SELECT * FROM inventory_adjustment WHERE request_key = #{requestKey}")
    InventoryAdjustment selectByRequestKey(@Param("requestKey") String requestKey);

    /** 当日已有序列最大值（单号生成用；uk_adjust_no 兜底并发撞号） */
    @Select("SELECT COUNT(*) FROM inventory_adjustment WHERE adjust_no LIKE CONCAT(#{prefix}, '%')")
    long countByAdjustNoPrefix(@Param("prefix") String prefix);
}
