package com.cloudmart.payment.reconciliation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ReconciliationRunMapper extends BaseMapper<ReconciliationRun> {

    /** T11：同业务日同层级只允许一次运行（uk(business_date,scope)）——重复执行返回既有运行 */
    @Select("SELECT * FROM reconciliation_run WHERE business_date = #{businessDate} AND scope = #{scope} LIMIT 1")
    ReconciliationRun findByDateAndScope(@Param("businessDate") java.time.LocalDate businessDate,
                                         @Param("scope") String scope);
}
