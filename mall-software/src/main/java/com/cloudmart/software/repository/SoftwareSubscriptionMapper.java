package com.cloudmart.software.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.software.entity.SoftwareSubscription;

/** 软件订阅 Mapper（P1）：id 兼作 fanout 游标键 */
public interface SoftwareSubscriptionMapper extends BaseMapper<SoftwareSubscription> {
}
