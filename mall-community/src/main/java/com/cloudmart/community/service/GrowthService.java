package com.cloudmart.community.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.community.vo.CheckInResultVO;
import com.cloudmart.community.vo.ExpLogVO;
import com.cloudmart.community.vo.LevelConfigVO;
import com.cloudmart.community.vo.UserLevelVO;

import java.time.LocalDate;
import java.util.List;

public interface GrowthService {

    CheckInResultVO checkIn(Long userId);

    boolean isCheckedInToday(Long userId);

    UserLevelVO getUserLevel(Long userId);

    void addExp(Long userId, int exp, String source, Long bizId, String description);

    Page<ExpLogVO> getExpLogs(Long userId, int page, int size);

    List<LevelConfigVO> getLevelConfigs();

    List<LocalDate> getCheckInCalendar(Long userId, int year, int month);

    int getContinuousDays(Long userId);
}
