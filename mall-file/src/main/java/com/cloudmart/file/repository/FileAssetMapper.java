package com.cloudmart.file.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.file.entity.FileAsset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface FileAssetMapper extends BaseMapper<FileAsset> {

    /** S01 引用登记协议：CAS READY → DELETING（引用登记只能挂 READY 资产，竞态窗口关闭） */
    @Update("UPDATE file_asset SET status = 'DELETING', updated_at = NOW(3) "
            + "WHERE id = #{fileId} AND status = 'READY'")
    int markDeleting(@Param("fileId") Long fileId);
}
