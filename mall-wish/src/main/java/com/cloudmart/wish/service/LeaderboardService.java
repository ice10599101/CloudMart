package com.cloudmart.wish.service;

import com.cloudmart.wish.entity.LeaderboardConfig;
import com.cloudmart.wish.vo.LeaderboardEntryVO;

import java.util.List;

/**
 * 排行榜服务（Sprint 2.7，文档 2.9/2.7）。
 */
public interface LeaderboardService {

    /** 重建四榜单 ZSet（幂等；mall-job 每 10 分钟调度 + 内部端点手动触发） */
    void refreshAll();

    /**
     * 读榜（Redis ZSet 命中，P95 < 200ms；空数据返回空数组）。
     *
     * @param type  榜单类型
     * @param limit Top N（≤100）
     */
    List<LeaderboardEntryVO> getBoard(LeaderboardType type, int limit);

    /**
     * 榜单类型（文档 2.9：HOT/WARM/PERSISTENCE/SPARK）。
     *
     * <p>数据源（排行榜计算策略文档）：HOT=wish.light_count（心愿维度）/
     * WARM=wish.bless_count（心愿维度）/ PERSISTENCE=wish_user_stat.
     * total_checkin_days（用户维度）/ SPARK=total_helped（用户维度）。</p>
     */
    enum LeaderboardType {
        HOT(true),
        WARM(true),
        PERSISTENCE(false),
        SPARK(false);

        private final boolean wishBoard;

        LeaderboardType(boolean wishBoard) {
            this.wishBoard = wishBoard;
        }

        public boolean isWishBoard() {
            return wishBoard;
        }
    }
}
