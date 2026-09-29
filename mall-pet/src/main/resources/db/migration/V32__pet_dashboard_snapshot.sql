-- P2-3：宠物看板每日快照——历史日指标小时级增量落表，看板读"快照(历史) + 实时(当日)"合并，
-- 避免数据量增长后每次打开看板都全量聚合多表

CREATE TABLE IF NOT EXISTS `pet_dashboard_daily_snapshot` (
    `id`          BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `stat_date`   DATE NOT NULL COMMENT '统计日(UTC 自然日)',
    `metric_key`  VARCHAR(32) NOT NULL COMMENT '指标键：new_pets/active_pets/activities/wall_messages/visits/battles',
    `metric_value` BIGINT NOT NULL DEFAULT 0 COMMENT '指标值',
    `created_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
    `updated_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_dashboard_snapshot` (`id`),
    UNIQUE KEY `uk_snapshot_date_metric` (`stat_date`, `metric_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物看板每日快照(调度器小时级增量写入)';
