package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.community.dto.CreateLevelConfigRequest;
import com.cloudmart.community.dto.UpdateLevelConfigRequest;
import com.cloudmart.community.entity.DailyCheckIn;
import com.cloudmart.community.entity.LevelConfig;
import com.cloudmart.community.repository.DailyCheckInMapper;
import com.cloudmart.community.repository.LevelConfigMapper;
import com.cloudmart.community.service.AdminGrowthService;
import com.cloudmart.community.vo.LevelConfigVO;
import com.cloudmart.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class AdminGrowthServiceImpl implements AdminGrowthService {

    private final LevelConfigMapper levelConfigMapper;
    private final DailyCheckInMapper dailyCheckInMapper;

    public AdminGrowthServiceImpl(LevelConfigMapper levelConfigMapper,
                                  DailyCheckInMapper dailyCheckInMapper) {
        this.levelConfigMapper = levelConfigMapper;
        this.dailyCheckInMapper = dailyCheckInMapper;
    }

    @Override
    @Transactional
    public LevelConfigVO createLevelConfig(CreateLevelConfigRequest request) {
        Long existing = levelConfigMapper.selectCount(
                new LambdaQueryWrapper<LevelConfig>().eq(LevelConfig::getLevel, request.level())
        );
        if (existing > 0) {
            throw new BusinessException("LEVEL_ALREADY_EXISTS", "等级 " + request.level() + " 已存在");
        }

        LevelConfig config = new LevelConfig();
        config.setLevel(request.level());
        config.setTitle(request.title());
        config.setMinExp(request.minExp());
        config.setIcon(request.icon());
        config.setBenefits(request.benefits());
        config.setStatus(1);
        levelConfigMapper.insert(config);

        return toLevelConfigVO(config);
    }

    @Override
    @Transactional
    public LevelConfigVO updateLevelConfig(Long id, UpdateLevelConfigRequest request) {
        LevelConfig config = levelConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException("LEVEL_CONFIG_NOT_FOUND", "等级配置不存在");
        }

        if (request.title() != null) {
            config.setTitle(request.title());
        }
        if (request.minExp() != null) {
            config.setMinExp(request.minExp());
        }
        if (request.icon() != null) {
            config.setIcon(request.icon());
        }
        if (request.benefits() != null) {
            config.setBenefits(request.benefits());
        }
        if (request.status() != null) {
            config.setStatus(request.status());
        }
        levelConfigMapper.updateById(config);

        return toLevelConfigVO(config);
    }

    @Override
    @Transactional
    public void deleteLevelConfig(Long id) {
        LevelConfig config = levelConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException("LEVEL_CONFIG_NOT_FOUND", "等级配置不存在");
        }
        levelConfigMapper.deleteById(id);
    }

    @Override
    public Page<LevelConfigVO> listLevelConfigs(int page, int size) {
        LambdaQueryWrapper<LevelConfig> wrapper = new LambdaQueryWrapper<LevelConfig>()
                .orderByAsc(LevelConfig::getLevel);

        Page<LevelConfig> configPage = levelConfigMapper.selectPage(new Page<>(page, size), wrapper);

        List<LevelConfigVO> voList = configPage.getRecords().stream()
                .map(this::toLevelConfigVO)
                .toList();

        Page<LevelConfigVO> resultPage = new Page<>(configPage.getCurrent(), configPage.getSize(), configPage.getTotal());
        resultPage.setRecords(voList);
        return resultPage;
    }

    @Override
    public long getTotalCheckIns() {
        return dailyCheckInMapper.selectCount(new LambdaQueryWrapper<>());
    }

    @Override
    public long getTodayCheckIns() {
        return dailyCheckInMapper.selectCount(
                new LambdaQueryWrapper<DailyCheckIn>()
                        .eq(DailyCheckIn::getCheckInDate, LocalDate.now())
        );
    }

    private LevelConfigVO toLevelConfigVO(LevelConfig config) {
        return new LevelConfigVO(
                config.getId(),
                config.getLevel(),
                config.getTitle(),
                config.getMinExp(),
                config.getIcon(),
                config.getBenefits(),
                config.getStatus()
        );
    }

    @Override
    @Transactional
    public void updateLevelConfigStatus(Long id, Integer status) {
        LevelConfig config = levelConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException("LEVEL_CONFIG_NOT_FOUND", "等级配置不存在");
        }
        config.setStatus(status);
        levelConfigMapper.updateById(config);
    }
}
