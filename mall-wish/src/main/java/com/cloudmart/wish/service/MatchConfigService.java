package com.cloudmart.wish.service;

import com.cloudmart.wish.entity.MatchConfig;

import java.util.List;

/**
 * 匹配算法配置服务（Sprint 2.6，文档 2.6 验收：权重可配置实时生效）。
 */
public interface MatchConfigService {

    /** 读取 double 配置（缺省/非法回退默认值，Fail-Open） */
    double getDoubleConfig(String key, double defaultValue);

    /** 读取 int 配置（缺省/非法回退默认值，Fail-Open） */
    int getIntConfig(String key, int defaultValue);

    /** 全量配置列表（管理端） */
    List<MatchConfig> listConfigs();

    /**
     * 更新配置（即时失效缓存实时生效）。
     *
     * @throws com.cloudmart.common.exception.BusinessException
     *         WISH_MATCH_CONFIG_INVALID（值非法）；键不存在按 400 WISH_VALIDATION_ERROR
     */
    MatchConfig updateConfig(String configKey, String configValue, Long adminUserId);
}
