package com.cloudmart.wish.service;

import com.cloudmart.wish.entity.LeaderboardConfig;

import java.util.List;

/**
 * 排行榜配置服务（Sprint 2.7，文档 2.7 管理后台：配置修改实时生效）。
 */
public interface LeaderboardConfigService {

    String getStringConfig(String key, String defaultValue);

    int getIntConfig(String key, int defaultValue);

    List<LeaderboardConfig> listConfigs();

    LeaderboardConfig updateConfig(String configKey, String configValue, Long adminUserId);
}
