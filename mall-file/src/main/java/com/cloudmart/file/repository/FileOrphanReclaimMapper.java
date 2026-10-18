package com.cloudmart.file.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.file.entity.FileOrphanReclaim;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface FileOrphanReclaimMapper extends BaseMapper<FileOrphanReclaim> {

    /** T27：回收完成——CAS PENDING → RECLAIMED，清空错误；幂等（已回收影响 0 行） */
    @Update("UPDATE file_orphan_reclaim SET status = 'RECLAIMED', error = NULL, "
            + "next_retry_at = NULL, updated_at = UTC_TIMESTAMP(3) "
            + "WHERE id = #{id} AND status = 'PENDING'")
    int markReclaimed(@Param("id") Long id);

    /** T27：回收失败登记——保持 PENDING，记录错误并指数退避（与 file_asset 同策略） */
    @Update("UPDATE file_orphan_reclaim SET status = 'PENDING', error = #{error}, attempts = attempts + 1, "
            + "next_retry_at = DATE_ADD(UTC_TIMESTAMP(3), INTERVAL LEAST(1 << LEAST(attempts, 6), 60) MINUTE), "
            + "updated_at = UTC_TIMESTAMP(3) "
            + "WHERE id = #{id} AND status = 'PENDING'")
    int markReclaimFailed(@Param("id") Long id, @Param("error") String error);

    /** T27：恢复扫描——选出到期可重试的 PENDING 回收义务，限量分批 */
    @Select("SELECT * FROM file_orphan_reclaim WHERE status = 'PENDING' "
            + "AND (next_retry_at IS NULL OR next_retry_at <= #{before}) "
            + "ORDER BY updated_at ASC LIMIT #{limit}")
    List<FileOrphanReclaim> selectDuePending(@Param("before") LocalDateTime before, @Param("limit") int limit);
}
