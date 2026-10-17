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

    /**
     * OPS-02：终态清租约带 owner——旧执行者无法清掉接管者（新 owner）的租约行，
     * 防止崩溃前的旧实例覆盖接管后的状态。
     */
    @Update("UPDATE wish_data_export SET lease_owner = NULL, lease_until = NULL, "
            + "updated_at = NOW(3) WHERE id = #{taskId} AND lease_owner = #{owner}")
    int clearLease(@Param("taskId") Long taskId, @Param("owner") String owner);

    /**
     * OPS-02：完成态 CAS——只允许当前租约持有者把任务置 SUCCESS；
     * 返回 0 表示租约已被接管（本实例的结果作废，不覆盖新执行者）。
     */
    @Update("UPDATE wish_data_export SET status = 'SUCCESS', content_enc = #{contentEnc}, "
            + "content_sha256 = #{cipherSha}, lease_owner = NULL, lease_until = NULL, updated_at = NOW(3) "
            + "WHERE id = #{taskId} AND status = 'PROCESSING' AND lease_owner = #{owner}")
    int completeByOwner(@Param("taskId") Long taskId, @Param("owner") String owner,
                        @Param("contentEnc") String contentEnc, @Param("cipherSha") String cipherSha);

    /**
     * OPS-02：失败态 CAS——同上，带 owner 条件。
     */
    @Update("UPDATE wish_data_export SET status = 'FAILED', fail_reason = #{reason}, "
            + "lease_owner = NULL, lease_until = NULL, updated_at = NOW(3) "
            + "WHERE id = #{taskId} AND status = 'PROCESSING' AND lease_owner = #{owner}")
    int failByOwner(@Param("taskId") Long taskId, @Param("owner") String owner,
                    @Param("reason") String reason);
}
