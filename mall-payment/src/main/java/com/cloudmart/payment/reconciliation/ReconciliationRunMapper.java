package com.cloudmart.payment.reconciliation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ReconciliationRunMapper extends BaseMapper<ReconciliationRun> {

    /** T11：同业务日同层级只允许一次运行（uk(business_date,scope)）——重复执行返回既有运行 */
    @Select("SELECT * FROM reconciliation_run WHERE business_date = #{businessDate} AND scope = #{scope} LIMIT 1")
    ReconciliationRun findByDateAndScope(@Param("businessDate") java.time.LocalDate businessDate,
                                         @Param("scope") String scope);

    /** FAILED 运行重试认领（CAS）：仅当仍处于 FAILED 时重置为 RUNNING 并清空上次中断的统计，防并发双认领 */
    @Update("UPDATE reconciliation_run SET status = 'RUNNING', total_checked = NULL, total_diff = NULL, "
            + "started_at = NOW(), finished_at = NULL WHERE id = #{id} AND status = 'FAILED'")
    int claimFailedRun(@Param("id") Long id);
}
