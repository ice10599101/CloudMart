package com.cloudmart.wish.service;

import com.cloudmart.wish.entity.GrayscaleConfig;

import java.util.List;
import java.util.Map;

/**
 * 灰度控制服务（Sprint 2.8，文档 2.8：灰度比例配置 + 灰度路由 + 回滚操作）。
 */
public interface GrayscaleService {

    /**
     * 单功能灰度命中判定（同一用户恒命中同一档；匿名仅全量放行）。
     */
    boolean isEnabled(Long userId, String featureKey);

    /**
     * 批量判定（公开 flags 接口）。
     *
     * @param featureKeys 功能键清单（空=全部）
     */
    Map<String, Boolean> flagsOf(Long userId, List<String> featureKeys);

    /** 全量灰度配置（管理端） */
    List<GrayscaleConfig> listConfigs();

    /**
     * 更新灰度比例（0/5/20/50/100 之外的值按安全口径取最近档位；
     * 回滚 = 置 0，配置回填缓存实时生效）。
     */
    GrayscaleConfig updateRatio(String featureKey, int grayRatio, Long adminUserId);
}
