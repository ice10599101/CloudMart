package com.cloudmart.file.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.file.entity.FileAsset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface FileAssetMapper extends BaseMapper<FileAsset> {

    /** S01 引用登记协议：CAS READY → DELETING（引用登记只能挂 READY 资产，竞态窗口关闭） */
    @Update("UPDATE file_asset SET status = 'DELETING', updated_at = UTC_TIMESTAMP(3) "
            + "WHERE id = #{fileId} AND status = 'READY'")
    int markDeleting(@Param("fileId") Long fileId);

    /** T15：资产行锁——绑定与删除在同一行锁协议内执行（检查-执行竞态窗口关闭） */
    @Select("SELECT * FROM file_asset WHERE id = #{fileId} FOR UPDATE")
    FileAsset selectByIdForUpdate(@Param("fileId") Long fileId);

    /**
     * T27：删除完成——CAS DELETING → DELETED，并清空恢复字段。
     * 幂等：已 DELETED 或不存在时影响 0 行（调用方无需据此报错）。
     */
    @Update("UPDATE file_asset SET status = 'DELETED', delete_error = NULL, "
            + "next_retry_at = NULL, updated_at = UTC_TIMESTAMP(3) "
            + "WHERE id = #{fileId} AND status = 'DELETING'")
    int markDeleted(@Param("fileId") Long fileId);

    /**
     * T27：删除失败登记——保持 DELETING（不放回 READY 让新引用进入），记录错误并指数退避。
     * 退避：1 分钟起，每次翻倍，上限 60 分钟（attempts 从 0 计：1,2,4,8,16,32,60...）。
     */
    @Update("UPDATE file_asset SET delete_error = #{error}, delete_attempts = delete_attempts + 1, "
            + "next_retry_at = DATE_ADD(UTC_TIMESTAMP(3), INTERVAL LEAST(1 << LEAST(delete_attempts, 6), 60) MINUTE), "
            + "updated_at = UTC_TIMESTAMP(3) "
            + "WHERE id = #{fileId} AND status = 'DELETING'")
    int markDeleteFailed(@Param("fileId") Long fileId, @Param("error") String error);

    /**
     * T27：恢复扫描——选出到期可重试的 DELETING 资产（next_retry_at 为空或已到期），
     * 按最早更新优先，限量分批。
     */
    @Select("SELECT * FROM file_asset WHERE status = 'DELETING' "
            + "AND (next_retry_at IS NULL OR next_retry_at <= #{before}) "
            + "ORDER BY updated_at ASC LIMIT #{limit}")
    List<FileAsset> selectDueDeleting(@Param("before") LocalDateTime before, @Param("limit") int limit);
}
