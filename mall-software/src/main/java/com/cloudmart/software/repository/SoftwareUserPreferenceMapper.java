package com.cloudmart.software.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.software.entity.SoftwareUserPreference;

/** 软件更新提醒偏好 Mapper（P1）：UNIQUE(user_id)，缺省视为开启 */
public interface SoftwareUserPreferenceMapper extends BaseMapper<SoftwareUserPreference> {
}
