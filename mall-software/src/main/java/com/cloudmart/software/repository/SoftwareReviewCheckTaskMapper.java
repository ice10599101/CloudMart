package com.cloudmart.software.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.software.entity.SoftwareReviewCheckTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * SW04 自动审核检查任务 Mapper：租约领取 + fencing 回写。
 *
 * <p>终态与退避回写必须同时匹配 lease_version 与 locked_by——租约过期被他人
 * 接管后，旧工作者不得覆盖新工作者的结果。撤回/人工处理会直接取消任务，
 * 因此终态更新同时要求 revision 仍处于 CHECKING（由服务层事务内复核）。</p>
 */
@Mapper
public interface SoftwareReviewCheckTaskMapper extends BaseMapper<SoftwareReviewCheckTask> {

    /** 到期可领取任务（含 RUNNING 但租约已过期——宕机续跑） */
    @Select("""
            SELECT * FROM software_review_check_task
            WHERE status IN ('PENDING', 'RUNNING')
              AND (next_retry_at IS NULL OR next_retry_at <= #{now})
              AND (lease_until IS NULL OR lease_until <= #{now})
            ORDER BY id
            LIMIT 1
            """)
    SoftwareReviewCheckTask findDue(@Param("now") LocalDateTime now);

    /** 领取：租约版本 CAS，成功者独占 */
    @Update("""
            UPDATE software_review_check_task
            SET status = 'RUNNING', locked_by = #{owner}, locked_at = #{now},
                lease_until = #{leaseUntil}, lease_version = lease_version + 1,
                updated_at = #{now}
            WHERE id = #{id} AND status IN ('PENDING', 'RUNNING')
              AND lease_version = #{expectedVersion}
              AND (lease_until IS NULL OR lease_until <= #{now})
            """)
    int claim(@Param("id") Long id, @Param("owner") String owner,
              @Param("leaseUntil") LocalDateTime leaseUntil,
              @Param("expectedVersion") Integer expectedVersion,
              @Param("now") LocalDateTime now);

    /** 终态/退避回写（fencing：租约版本 + 执行者双条件） */
    @Update("""
            UPDATE software_review_check_task
            SET status = #{status}, attempts = #{attempts}, next_retry_at = #{nextRetryAt},
                last_error = #{lastError}, locked_by = NULL, lease_until = NULL,
                finished_at = #{finishedAt}, updated_at = #{now}
            WHERE id = #{id} AND lease_version = #{leaseVersion} AND locked_by = #{owner}
            """)
    int finish(@Param("id") Long id, @Param("status") String status,
               @Param("attempts") int attempts,
               @Param("nextRetryAt") LocalDateTime nextRetryAt,
               @Param("lastError") String lastError,
               @Param("finishedAt") LocalDateTime finishedAt,
               @Param("leaseVersion") Integer leaseVersion,
               @Param("owner") String owner, @Param("now") LocalDateTime now);

    /** 撤回/删除时取消检查（仅未进入终态且无有效执行租约的任务） */
    @Update("""
            UPDATE software_review_check_task
            SET status = 'CANCELLED', lease_until = NULL, locked_by = NULL,
                finished_at = #{now}, updated_at = #{now}
            WHERE revision_id = #{revisionId}
              AND status IN ('PENDING', 'RUNNING', 'DEAD_LETTER')
            """)
    int cancelByRevision(@Param("revisionId") Long revisionId,
                         @Param("now") LocalDateTime now);

    /** 后台运维：人工重试卡住/死信任务（保留 attempts 与内容身份） */
    @Update("""
            UPDATE software_review_check_task
            SET status = 'PENDING', next_retry_at = #{now}, last_error = NULL,
                lease_until = NULL, locked_by = NULL, finished_at = NULL, updated_at = #{now}
            WHERE id = #{id}
              AND status IN ('PENDING', 'RUNNING', 'DEAD_LETTER')
              AND (lease_until IS NULL OR lease_until <= #{now})
            """)
    int manualRetry(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Select("""
            SELECT * FROM software_review_check_task
            WHERE revision_id = #{revisionId}
            LIMIT 1
            """)
    SoftwareReviewCheckTask findByRevision(@Param("revisionId") Long revisionId);

    @Select("""
            SELECT COUNT(*) FROM software_review_check_task
            WHERE status IN ('PENDING', 'RUNNING')
            """)
    long countQueued();

    @Select("""
            SELECT COUNT(*) FROM software_review_check_task
            WHERE status = 'DEAD_LETTER'
            """)
    long countDeadLetter();

    /** 最早进入 CHECKING 的提交等待秒数（0 表示无积压） */
    @Select("""
            SELECT COALESCE(TIMESTAMPDIFF(SECOND, MIN(submitted_at), #{now}), 0)
            FROM software_revision WHERE review_status = 'CHECKING'
            """)
    long oldestCheckingSeconds(@Param("now") LocalDateTime now);
}
