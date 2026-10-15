package com.cloudmart.software.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.software.entity.Software;

/** 软件主体 Mapper */
public interface SoftwareMapper extends BaseMapper<Software> {

    /** 日获取去重事实首次落库成功后的累计计数原子递增（6.9；仅同事务调用） */
    @org.apache.ibatis.annotations.Update(
            "UPDATE software SET link_access_count = link_access_count + 1 WHERE id = #{softwareId}")
    int incrementLinkAccessCount(@org.apache.ibatis.annotations.Param("softwareId") Long softwareId);
}
