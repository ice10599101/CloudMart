package com.cloudmart.community.scheduler;

import com.cloudmart.community.service.LikeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 点赞数同步定时任务：每 20 秒从 Redis ZSet 弹出待同步的点赞数变更，通过 MQ 异步更新数据库。
 *
 * <p>最终一致性模型：Redis Set 是点赞记录的实时来源，数据库 like_count 字段允许短暂延迟。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LikeTimesScheduler {

    private final LikeService likeService;

    @Scheduled(fixedDelay = 20_000)
    public void syncLikedTimes() {
        try {
            likeService.syncLikedTimesToMQ();
        } catch (Exception e) {
            log.error("Failed to sync like-times to MQ", e);
        }
    }
}
