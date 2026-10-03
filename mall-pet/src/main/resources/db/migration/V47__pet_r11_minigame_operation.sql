-- V47 (R11): 小游戏操作唯一事实表——每窗一次有效操作，杜绝并发提交丢操作/覆盖终态
-- 原实现：ops 整行 JSON 读改写（updateById 全实体覆盖，实体无版本）——
-- 并发提交相互覆盖、与 settle 竞争时旧 ACTIVE 实体可把 SETTLED 写回 ACTIVE。

CREATE TABLE IF NOT EXISTS `pet_minigame_operation` (
    `id`            BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `round_id`      BIGINT UNSIGNED NOT NULL COMMENT '对局ID',
    `user_id`       BIGINT UNSIGNED NOT NULL COMMENT '用户ID(归属校验/扫描)',
    `window_index`  INT NOT NULL COMMENT '时间窗序号(1基，由 floor((serverNow-startedAt)/windowMs) 决定)',
    `slot`          VARCHAR(16) NOT NULL COMMENT '命中的目标槽位(LEFT/CENTER/RIGHT)',
    `accepted`      TINYINT NOT NULL DEFAULT 1 COMMENT '是否有效接受(1=是)',
    `server_time`   DATETIME NOT NULL COMMENT '服务端接收时间(UTC，唯一权威)',
    `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    PRIMARY KEY `pk_pet_minigame_operation` (`id`),
    UNIQUE KEY `uk_minigame_operation_window` (`round_id`, `window_index`),
    INDEX `idx_minigame_operation_round` (`round_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='小游戏窗口操作事实(每窗至多一次)';
