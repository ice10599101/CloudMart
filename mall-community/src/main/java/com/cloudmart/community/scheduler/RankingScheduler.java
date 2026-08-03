package com.cloudmart.community.scheduler;

import com.cloudmart.community.service.RankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 排行榜定时任务：每月 1 号凌晨 2 点将上月榜单持久化到 MySQL。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankingScheduler {

    private final RankingService rankingService;

    @Scheduled(cron = "0 0 2 1 * ?")
    public void persistLastMonthRanking() {
        log.info("开始持久化上月排行榜数据");
        try {
            rankingService.persistLastMonthRanking();
            log.info("上月排行榜数据持久化完成");
        } catch (Exception e) {
            log.error("持久化上月排行榜数据失败", e);
        }
    }
}
