package com.cloudmart.software.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.software.entity.SoftwareRating;

/** 软件评分 Mapper（P1）：UNIQUE(user_id,software_id) 为最终正确性保障 */
public interface SoftwareRatingMapper extends BaseMapper<SoftwareRating> {
}
