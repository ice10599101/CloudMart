package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.community.entity.RankingSeason;
import com.cloudmart.community.repository.RankingSeasonMapper;
import com.cloudmart.community.service.AdminRankingService;
import com.cloudmart.community.vo.RankingSeasonVO;
import com.cloudmart.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 排行榜管理后台服务实现。
 */
@Service
@RequiredArgsConstructor
public class AdminRankingServiceImpl implements AdminRankingService {

    private final RankingSeasonMapper rankingSeasonMapper;

    @Override
    public Page<RankingSeasonVO> listSeasons(int page, int size, Integer status) {
        LambdaQueryWrapper<RankingSeason> wrapper = new LambdaQueryWrapper<RankingSeason>()
                .orderByDesc(RankingSeason::getSeasonKey);
        if (status != null) {
            wrapper.eq(RankingSeason::getStatus, status);
        }

        Page<RankingSeason> seasonPage = rankingSeasonMapper.selectPage(new Page<>(page, size), wrapper);

        List<RankingSeasonVO> voList = seasonPage.getRecords().stream()
                .map(this::toVO)
                .toList();

        Page<RankingSeasonVO> resultPage = new Page<>(seasonPage.getCurrent(), seasonPage.getSize(), seasonPage.getTotal());
        resultPage.setRecords(voList);
        return resultPage;
    }

    @Override
    @Transactional
    public void updateSeasonStatus(Long seasonId, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("INVALID_STATUS", "状态值无效，仅支持 0-进行中 或 1-已归档");
        }

        RankingSeason season = rankingSeasonMapper.selectById(seasonId);
        if (season == null) {
            throw new BusinessException("SEASON_NOT_FOUND", "赛季不存在");
        }

        season.setStatus(status);
        rankingSeasonMapper.updateById(season);
    }

    private RankingSeasonVO toVO(RankingSeason season) {
        return new RankingSeasonVO(
                season.getId(),
                season.getName(),
                season.getSeasonKey(),
                season.getStartDate(),
                season.getEndDate(),
                season.getStatus()
        );
    }
}
