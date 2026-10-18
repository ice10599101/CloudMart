package com.cloudmart.software.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.software.entity.SoftwareFanoutTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * fanout 任务 Mapper（方案 9.4，P1）：领取带租约版本 fencing——
 * 游标推进/终态更新必须同时匹配 lease_version 与 lease_owner，宕机后被接管者
 * 的新租约不能让旧工作者继续写游标。
 */
@Mapper
public interface SoftwareFanoutTaskMapper extends BaseMapper<SoftwareFanoutTask> {

    /** 到期可领取任务（含 RUNNING 但租约已过期——宕机续跑） */
    @Select("""
            SELECT * FROM software_fanout_task
            WHERE status IN ('PENDING', 'RUNNING')
              AND (next_retry_at IS NULL OR next_retry_at <= #{now})
              AND (lease_until IS NULL OR lease_until <= #{now})
            ORDER BY id
            LIMIT 1
            """)
    SoftwareFanoutTask findDue(@Param("now") LocalDateTime now);

    /** 领取：租约版本 CAS，成功者独占 */
    @Update("""
            UPDATE software_fanout_task
            SET status = 'RUNNING', lease_owner = #{owner}, lease_until = #{leaseUntil},
                lease_version = lease_version + 1, updated_at = #{now}
            WHERE id = #{id} AND lease_version = #{expectedVersion}
              AND (lease_until IS NULL OR lease_until <= #{now})
            """)
    int claim(@Param("id") Long id, @Param("owner") String owner,
              @Param("leaseUntil") LocalDateTime leaseUntil,
              @Param("expectedVersion") Integer expectedVersion,
              @Param("now") LocalDateTime now);

    /** 推进游标（fencing：版本+拥有者双条件） */
    @Update("""
            UPDATE software_fanout_task
            SET cursor_subscription_id = #{cursor}, updated_at = #{now}
            WHERE id = #{id} AND lease_version = #{leaseVersion} AND lease_owner = #{owner}
            """)
    int advanceCursor(@Param("id") Long id, @Param("cursor") Long cursor,
                      @Param("leaseVersion") Integer leaseVersion,
                      @Param("owner") String owner, @Param("now") LocalDateTime now);

    /** 续租（长批次间） */
    @Update("""
            UPDATE software_fanout_task
            SET lease_until = #{leaseUntil}, updated_at = #{now}
            WHERE id = #{id} AND lease_version = #{leaseVersion} AND lease_owner = #{owner}
            """)
    int renewLease(@Param("id") Long id, @Param("leaseUntil") LocalDateTime leaseUntil,
                   @Param("leaseVersion") Integer leaseVersion,
                   @Param("owner") String owner, @Param("now") LocalDateTime now);

    /** 终态（DONE/FAILED）或失败退避（PENDING），fencing 条件同游标推进 */
    @Update("""
            UPDATE software_fanout_task
            SET status = #{status}, attempts = #{attempts}, next_retry_at = #{nextRetryAt},
                last_error = #{lastError}, lease_owner = NULL, lease_until = NULL,
                updated_at = #{now}
            WHERE id = #{id} AND lease_version = #{leaseVersion} AND lease_owner = #{owner}
            """)
    int finish(@Param("id") Long id, @Param("status") String status,
               @Param("attempts") int attempts,
               @Param("nextRetryAt") LocalDateTime nextRetryAt,
               @Param("lastError") String lastError,
               @Param("leaseVersion") Integer leaseVersion,
               @Param("owner") String owner, @Param("now") LocalDateTime now);

    /** SW10：按软件+发布序号定位任务（投递重试需用任务冻结的 revisionId，不得借用最新指针） */
    @Select("""
            SELECT * FROM software_fanout_task
            WHERE software_id = #{softwareId} AND publication_seq = #{publicationSeq}
            LIMIT 1
            """)
    SoftwareFanoutTask findBySoftwareAndSeq(@Param("softwareId") Long softwareId,
                                            @Param("publicationSeq") Integer publicationSeq);

    /** SW10：人工重试——保持原 eventId 语义（seq/revisionId 不变），仅重新入队 */
    @Update("""
            UPDATE software_fanout_task
            SET status = 'PENDING', next_retry_at = #{now},
                lease_owner = NULL, lease_until = NULL, last_error = NULL, updated_at = #{now}
            WHERE id = #{id} AND status IN ('PENDING', 'RUNNING', 'FAILED')
            """)
    int manualRetry(@Param("id") Long id, @Param("now") LocalDateTime now);
}
