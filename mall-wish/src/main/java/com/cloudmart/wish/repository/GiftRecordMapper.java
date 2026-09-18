package com.cloudmart.wish.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.wish.entity.GiftRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 礼物赠送记录 Mapper（V37 迁移，全站虚拟礼物）。
 */
@Mapper
public interface GiftRecordMapper extends BaseMapper<GiftRecord> {
}
