package com.cloudmart.common.async.mapper;

import com.cloudmart.common.async.compensation.CompensationTaskEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 补偿任务 Mapper（ASYNC-01）：与 Outbox 相同的租约抢占与退避模型。
 */
@Mapper
public interface CompensationTaskMapper {

    @Insert("""
            INSERT IGNORE INTO compensation_task
              (task_id, action, aggregate_id, payload, status, attempts, next_retry_at, created_at, updated_at)
            VALUES
              (#{t.taskId}, #{t.action}, #{t.aggregateId}, #{t.payload}, 'PENDING', 0, NOW(3), NOW(3), NOW(3))
            """)
    int insertIfAbsent(@Param("t") CompensationTaskEntity task);

    @Select("""
            SELECT * FROM compensation_task
            WHERE locked_by = #{workerId}
              AND status = 'PROCESSING'
              AND locked_at >= DATE_SUB(NOW(3), INTERVAL #{leaseSeconds} SECOND)
            ORDER BY id
            LIMIT #{batch}
            """)
    List<CompensationTaskEntity> selectClaimed(@Param("workerId") String workerId,
                                               @Param("leaseSeconds") int leaseSeconds,
                                               @Param("batch") int batch);

    @Update("""
            UPDATE compensation_task
            SET status = 'PROCESSING', locked_by = #{workerId}, locked_at = NOW(3), updated_at = NOW(3)
            WHERE (status IN ('PENDING', 'FAILED') AND next_retry_at <= NOW(3))
               OR (status = 'PROCESSING' AND locked_at < DATE_SUB(NOW(3), INTERVAL #{leaseSeconds} SECOND))
            ORDER BY id
            LIMIT #{batch}
            """)
    int claimBatch(@Param("workerId") String workerId,
                   @Param("leaseSeconds") int leaseSeconds,
                   @Param("batch") int batch);

    @Update("""
            UPDATE compensation_task
            SET status = 'SUCCEEDED', updated_at = NOW(3)
            WHERE id = #{id} AND status = 'PROCESSING'
            """)
    int markSucceeded(@Param("id") Long id);

    @Update("""
            UPDATE compensation_task
            SET status = CASE WHEN attempts + 1 >= #{maxAttempts} THEN 'DEAD_LETTER' ELSE 'FAILED' END,
                attempts = attempts + 1,
                next_retry_at = CASE WHEN attempts + 1 >= #{maxAttempts} THEN NULL
                                     ELSE DATE_ADD(NOW(3), INTERVAL #{backoffMillis} MICROSECOND) END,
                last_error = #{lastError},
                locked_by = NULL,
                locked_at = NULL,
                updated_at = NOW(3)
            WHERE id = #{id} AND status = 'PROCESSING'
            """)
    int markFailure(@Param("id") Long id,
                    @Param("maxAttempts") int maxAttempts,
                    @Param("backoffMillis") long backoffMillis,
                    @Param("lastError") String lastError);

    @Update("""
            UPDATE compensation_task
            SET status = 'PENDING', next_retry_at = NOW(3), attempts = 0,
                last_error = NULL, locked_by = NULL, locked_at = NULL, updated_at = NOW(3)
            WHERE id = #{id} AND status = 'DEAD_LETTER'
            """)
    int retryDeadLetter(@Param("id") Long id);
}
