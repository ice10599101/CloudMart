package com.cloudmart.common.async.mapper;

import com.cloudmart.common.async.inbox.InboxRecordEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * Inbox 消费记录 Mapper（ASYNC-01）。
 *
 * <p>唯一键 (consumer, event_id)；INSERT IGNORE 抢位，PROCESSING 锁租约过期可接管。</p>
 */
@Mapper
public interface InboxRecordMapper {

    @Insert("""
            INSERT IGNORE INTO inbox_record
              (consumer, event_id, event_type, aggregate_id, status, attempts, locked_at, created_at, updated_at)
            VALUES
              (#{consumer}, #{eventId}, #{eventType}, #{aggregateId}, 'PROCESSING', 1, NOW(3), NOW(3), NOW(3))
            """)
    int insertProcessing(@Param("consumer") String consumer,
                         @Param("eventId") String eventId,
                         @Param("eventType") String eventType,
                         @Param("aggregateId") String aggregateId);

    @Select("""
            SELECT * FROM inbox_record
            WHERE consumer = #{consumer} AND event_id = #{eventId}
            """)
    InboxRecordEntity find(@Param("consumer") String consumer, @Param("eventId") String eventId);

    @Update("""
            UPDATE inbox_record
            SET status = 'PROCESSING', attempts = attempts + 1, locked_at = NOW(3), updated_at = NOW(3)
            WHERE consumer = #{consumer} AND event_id = #{eventId}
              AND (status = 'FAILED' OR (status = 'PROCESSING' AND locked_at < DATE_SUB(NOW(3), INTERVAL #{leaseSeconds} SECOND)))
            """)
    int takeOver(@Param("consumer") String consumer,
                 @Param("eventId") String eventId,
                 @Param("leaseSeconds") int leaseSeconds);

    @Update("""
            UPDATE inbox_record
            SET status = 'PROCESSED', processed_at = NOW(3), updated_at = NOW(3)
            WHERE consumer = #{consumer} AND event_id = #{eventId} AND status <> 'PROCESSED'
            """)
    int markProcessed(@Param("consumer") String consumer, @Param("eventId") String eventId);

    @Update("""
            UPDATE inbox_record
            SET status = 'FAILED', last_error = #{lastError}, locked_at = NULL, updated_at = NOW(3)
            WHERE consumer = #{consumer} AND event_id = #{eventId}
            """)
    int markFailed(@Param("consumer") String consumer,
                   @Param("eventId") String eventId,
                   @Param("lastError") String lastError);
}
