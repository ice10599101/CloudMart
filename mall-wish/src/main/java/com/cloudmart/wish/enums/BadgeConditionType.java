package com.cloudmart.wish.enums;

import com.cloudmart.wish.entity.WishUserStat;

/**
 * 徽章触发条件类型（condition JSON 的 type 字段，文档 1.2 ⑫ / 6.5）。
 *
 * <p>每种类型映射 {@link WishUserStat} 的一个累计指标；
 * 打卡类条件（TOTAL_CHECKIN_DAYS）在打卡功能上线后自然生效，
 * 引擎无需改动（声明式扩展：新增徽章仅插入 wish_badge 行）。</p>
 */
public enum BadgeConditionType {

    /** 累计创建心愿数（含软删，只增不减） */
    WISH_CREATED {
        @Override
        public int extractMetric(WishUserStat stat) {
            return stat.getTotalWishes() != null ? stat.getTotalWishes() : 0;
        }
    },

    /** 累计还愿数（历史事实，不回退） */
    WISH_FULFILLED {
        @Override
        public int extractMetric(WishUserStat stat) {
            return stat.getTotalFulfilled() != null ? stat.getTotalFulfilled() : 0;
        }
    },

    /** 累计帮助他人次数（点亮 + 匿名星光，MQ 异步累加） */
    TOTAL_HELPED {
        @Override
        public int extractMetric(WishUserStat stat) {
            return stat.getTotalHelped() != null ? stat.getTotalHelped() : 0;
        }
    },

    /** 累计打卡天数 */
    TOTAL_CHECKIN_DAYS {
        @Override
        public int extractMetric(WishUserStat stat) {
            return stat.getTotalCheckinDays() != null ? stat.getTotalCheckinDays() : 0;
        }
    };

    /**
     * 从用户统计中提取本条件对应的指标当前值。
     */
    public abstract int extractMetric(WishUserStat stat);
}
