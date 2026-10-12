package com.cloudmart.payment.reconciliation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ReconciliationRunMapper extends BaseMapper<ReconciliationRun> {

    /** 同业务日同层级只允许一次运行（uk(business_date,scope)）——重复执行返回既有运行 */
    @Select("SELECT * FROM reconciliation_run WHERE business_date = #{businessDate} AND scope = #{scope} LIMIT 1")
    ReconciliationRun findByDateAndScope(@Param("businessDate") java.time.LocalDate businessDate,
                                         @Param("scope") String scope);

    /**
     * R26：认领可恢复运行（PARTIAL/FAILED）——CAS 仅在仍处于可恢复状态时置 RUNNING 并发放新租约。
     * 不清零统计、不回退断点游标、不删除差异：续扫基于断点累加，差异证据（含人工处置）保留。
     */
    @Update("UPDATE reconciliation_run SET status = 'RUNNING', lease_owner = #{owner}, "
            + "lease_until = DATE_ADD(NOW(3), INTERVAL #{leaseSeconds} SECOND), heartbeat_at = NOW(3), "
            + "finished_at = NULL WHERE id = #{id} AND status IN ('PARTIAL', 'FAILED')")
    int claimResumableRun(@Param("id") Long id,
                          @Param("owner") String owner,
                          @Param("leaseSeconds") int leaseSeconds);

    /**
     * R26：接管失联 RUNNING（进程 kill/崩溃）——仅当租约已过期才允许 CAS 接管。
     * 同样保留统计/游标/差异：接管是"续跑"而非"重扫"。
     */
    @Update("UPDATE reconciliation_run SET status = 'RUNNING', lease_owner = #{owner}, "
            + "lease_until = DATE_ADD(NOW(3), INTERVAL #{leaseSeconds} SECOND), heartbeat_at = NOW(3) "
            + "WHERE id = #{id} AND status = 'RUNNING' "
            + "AND (lease_until IS NULL OR lease_until < NOW(3))")
    int takeoverStaleRun(@Param("id") Long id,
                         @Param("owner") String owner,
                         @Param("leaseSeconds") int leaseSeconds);
}
