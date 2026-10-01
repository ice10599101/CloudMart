package com.cloudmart.wish.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.wish.entity.DataExport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface DataExportMapper extends BaseMapper<DataExport> {

    /** W04：认领租约——PENDING → PROCESSING（多实例只有一个赢家） */
    @Update("UPDATE wish_data_export SET status = 'PROCESSING', lease_owner = #{owner}, "
            + "lease_until = #{leaseUntil}, updated_at = NOW(3) "
            + "WHERE id = #{taskId} AND status = 'PENDING'")
    int claimPending(@Param("taskId") Long taskId, @Param("owner") String owner,
                     @Param("leaseUntil") LocalDateTime leaseUntil);

    /** W04：接管过期租约——PROCESSING 且 lease_until 已过期（执行者崩溃残留） */
    @Update("UPDATE wish_data_export SET lease_owner = #{owner}, lease_until = #{leaseUntil}, "
            + "updated_at = NOW(3) WHERE id = #{taskId} AND status = 'PROCESSING' "
            + "AND lease_until IS NOT NULL AND lease_until < NOW(3)")
    int takeoverExpiredLease(@Param("taskId") Long taskId, @Param("owner") String owner,
                             @Param("leaseUntil") LocalDateTime leaseUntil);

    /** W04：续租（长任务执行中定期推进，防被误接管） */
    @Update("UPDATE wish_data_export SET lease_until = #{leaseUntil}, updated_at = NOW(3) "
            + "WHERE id = #{taskId} AND lease_owner = #{owner} AND status = 'PROCESSING'")
    int renewLease(@Param("taskId") Long taskId, @Param("owner") String owner,
                   @Param("leaseUntil") LocalDateTime leaseUntil);

    /** W04：终态清租约 */
    @Update("UPDATE wish_data_export SET lease_owner = NULL, lease_until = NULL, "
            + "updated_at = NOW(3) WHERE id = #{taskId}")
    int clearLease(@Param("taskId") Long taskId);
}
