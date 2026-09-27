package com.cloudmart.common.async.mapper;

import com.cloudmart.common.async.outbox.OutboxEventEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox 事件 Mapper（ASYNC-01）：注解 SQL，避免 XML。
 *
 * <p>关键语义：</p>
 * <ul>
 *   <li>{@link #insertIfAbsent}：INSERT IGNORE 保证同一 eventId 重放/重试不产生重复行；</li>
 *   <li>{@link #claimBatch}：单语句抢占（租约模型）——多实例并发领取互不重叠，
 *       SENDING 且锁过期的行可被接管（实例宕机恢复）；</li>
 *   <li>{@link #markFailure}：退避重试或转 DEAD_LETTER。</li>
 * </ul>
 */
@Mapper
public interface OutboxEventMapper {

    @Insert("""
            INSERT IGNORE INTO outbox_event
              (event_id, event_type, schema_version, aggregate_id, aggregate_version,
               request_id, payload, status, attempts, next_retry_at, created_at, updated_at)
            VALUES
              (#{e.eventId}, #{e.eventType}, #{e.schemaVersion}, #{e.aggregateId}, #{e.aggregateVersion},
               #{e.requestId}, #{e.payload}, 'PENDING', 0, NOW(3), NOW(3), NOW(3))
            """)
    int insertIfAbsent(@Param("e") OutboxEventEntity event);

    @Select("""
            SELECT * FROM outbox_event
            WHERE locked_by = #{workerId}
              AND status = 'SENDING'
              AND locked_at >= DATE_SUB(NOW(3), INTERVAL #{leaseSeconds} SECOND)
            ORDER BY id
            LIMIT #{batch}
            """)
    List<OutboxEventEntity> selectClaimed(@Param("workerId") String workerId,
                                          @Param("leaseSeconds") int leaseSeconds,
                                          @Param("batch") int batch);

    @Update("""
            UPDATE outbox_event
            SET status = 'SENDING', locked_by = #{workerId}, locked_at = NOW(3), updated_at = NOW(3)
            WHERE (status IN ('PENDING', 'FAILED') AND next_retry_at <= NOW(3))
               OR (status = 'SENDING' AND locked_at < DATE_SUB(NOW(3), INTERVAL #{leaseSeconds} SECOND))
            ORDER BY id
            LIMIT #{batch}
            """)
    int claimBatch(@Param("workerId") String workerId,
                   @Param("leaseSeconds") int leaseSeconds,
                   @Param("batch") int batch);

    @Update("""
            UPDATE outbox_event
            SET status = 'SENT', sent_at = NOW(3), updated_at = NOW(3)
            WHERE id = #{id}
            """)
    int markSent(@Param("id") Long id);

    @Update("""
            UPDATE outbox_event
            SET status = CASE WHEN attempts + 1 >= #{maxAttempts} THEN 'DEAD_LETTER' ELSE 'FAILED' END,
                attempts = attempts + 1,
                next_retry_at = CASE WHEN attempts + 1 >= #{maxAttempts} THEN NULL
                                     ELSE DATE_ADD(NOW(3), INTERVAL #{backoffMillis} MICROSECOND) END,
                last_error = #{lastError},
                locked_by = NULL,
                locked_at = NULL,
                updated_at = NOW(3)
            WHERE id = #{id} AND status = 'SENDING'
            """)
    int markFailure(@Param("id") Long id,
                    @Param("maxAttempts") int maxAttempts,
                    @Param("backoffMillis") long backoffMillis,
                    @Param("lastError") String lastError);

    /** 工作台重试：DEAD_LETTER 重置为待投递 */
    @Update("""
            UPDATE outbox_event
            SET status = 'PENDING', next_retry_at = NOW(3), attempts = 0,
                last_error = NULL, locked_by = NULL, locked_at = NULL, updated_at = NOW(3)
            WHERE id = #{id} AND status = 'DEAD_LETTER'
            """)
    int retryDeadLetter(@Param("id") Long id);

    @Select("SELECT * FROM outbox_event WHERE event_id = #{eventId}")
    OutboxEventEntity findByEventId(@Param("eventId") String eventId);
}
