package com.cloudmart.software.repository;

import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwarePublisherPermission;
import com.cloudmart.software.entity.SoftwareRevision;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 行锁协议 Mapper（方案 4.0/9.7）：锁顺序统一为资格行→软件主行→快照行；
 * 供投稿/审核事务在本地事务内按序加锁并复核（SELECT ... FOR UPDATE）。
 */
public interface SoftwareLockMapper {

    @Select("SELECT * FROM software_publisher_permission WHERE user_id = #{userId} FOR UPDATE")
    SoftwarePublisherPermission lockPublisherByUser(@Param("userId") Long userId);

    @Select("SELECT * FROM software WHERE id = #{id} FOR UPDATE")
    Software lockSoftware(@Param("id") Long softwareId);

    @Select("SELECT * FROM software_revision WHERE id = #{id} FOR UPDATE")
    SoftwareRevision lockRevision(@Param("id") Long revisionId);

    @Select("SELECT * FROM software_access_grant WHERE software_id = #{softwareId} "
            + "AND user_id = #{userId} FOR UPDATE")
    com.cloudmart.software.entity.SoftwareAccessGrant lockAccessGrant(
            @Param("softwareId") Long softwareId, @Param("userId") Long userId);
}
