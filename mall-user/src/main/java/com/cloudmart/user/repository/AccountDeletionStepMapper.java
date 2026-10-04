package com.cloudmart.user.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.user.entity.AccountDeletionStep;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 注销步骤台账 Mapper（T06）：租约认领/续期/结果回写全部条件更新——
 * 回写必须绑定当前租约持有者，失去租约的旧执行者迟到回写无效。
 */
@Mapper
public interface AccountDeletionStepMapper extends BaseMapper<AccountDeletionStep> {

    /** CAS 认领：PENDING/FAILED(到期) 且无有效租约 → RUNNING + 租约 */
    @Update("UPDATE user_account_deletion_step SET status = 'RUNNING', lease_owner = #{owner}, "
            + "lease_until = #{leaseUntil}, attempts = attempts + 1, updated_at = NOW(3) "
            + "WHERE id = #{stepId} AND (status IN ('PENDING','FAILED','BLOCKED')) "
            + "AND (next_retry_at IS NULL OR next_retry_at <= NOW(3)) "
            + "AND (lease_until IS NULL OR lease_until < NOW(3))")
    int claim(@Param("stepId") Long stepId, @Param("owner") String owner,
              @Param("leaseUntil") LocalDateTime leaseUntil);

    /** 步骤成功（仅租约持有者可回写） */
    @Update("UPDATE user_account_deletion_step SET status = 'SUCCESS', completed_at = NOW(3), "
            + "last_error = NULL, lease_until = NULL, updated_at = NOW(3) "
            + "WHERE id = #{stepId} AND status = 'RUNNING' AND lease_owner = #{owner}")
    int markSuccess(@Param("stepId") Long stepId, @Param("owner") String owner);

    /** 步骤失败（退避重试；BLOCKED 用于明确不可重试的硬阻断） */
    @Update("UPDATE user_account_deletion_step SET status = #{status}, next_retry_at = #{nextRetryAt}, "
            + "last_error = #{lastError}, lease_until = NULL, updated_at = NOW(3) "
            + "WHERE id = #{stepId} AND status = 'RUNNING' AND lease_owner = #{owner}")
    int markFailed(@Param("stepId") Long stepId, @Param("owner") String owner,
                   @Param("status") String status,
                   @Param("nextRetryAt") LocalDateTime nextRetryAt,
                   @Param("lastError") String lastError);
}
