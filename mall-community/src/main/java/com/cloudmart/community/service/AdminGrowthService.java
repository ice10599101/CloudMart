package com.cloudmart.community.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.community.dto.CreateLevelConfigRequest;
import com.cloudmart.community.dto.UpdateLevelConfigRequest;
import com.cloudmart.community.vo.LevelConfigVO;

public interface AdminGrowthService {

    LevelConfigVO createLevelConfig(CreateLevelConfigRequest request);

    LevelConfigVO updateLevelConfig(Long id, UpdateLevelConfigRequest request);

    void deleteLevelConfig(Long id);

    Page<LevelConfigVO> listLevelConfigs(int page, int size);

    long getTotalCheckIns();

    long getTodayCheckIns();

    void updateLevelConfigStatus(Long id, Integer status);
}
