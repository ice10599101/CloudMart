-- V10: B05 陪伴时长/积分/亲密度持久化——服务端会话与业务日额度
-- 现状：每次 60 秒心跳被单独除以 600 永远得不到 1 点；亲密度部分调用未落库。
-- 方案：
--   pet_companion_session：服务端会话（每用户至多一条 ACTIVE，多端只累计一份有效时间）；
--   pet_companion_daily：业务日（businessZone）累计有效秒数与已发积分，同事务保存时长/积分/已发计数。

CREATE TABLE IF NOT EXISTS `pet_companion_session` (
    `id`                 BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `user_id`            BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `pet_id`             BIGINT UNSIGNED NOT NULL COMMENT '陪伴的宠物ID',
    `status`             ENUM('ACTIVE','ENDED') NOT NULL DEFAULT 'ACTIVE' COMMENT '状态:有效/已结束(失效/停止/被新会话接管)',
    `started_at`         DATETIME NOT NULL COMMENT '会话开始时间(UTC,服务端基准)',
    `last_heartbeat_at`  DATETIME NOT NULL COMMENT '最近有效心跳时间(UTC,计时游标)',
    `last_seq`           BIGINT NOT NULL DEFAULT 0 COMMENT '最近已接受的心跳序号(客户端单调递增,重放去重)',
    `ended_at`           DATETIME DEFAULT NULL COMMENT '结束时间(UTC)',
    `end_reason`         ENUM('STOPPED','EXPIRED','SUPERSEDED') DEFAULT NULL COMMENT '结束原因:正常停止/心跳超时失效/被新会话替换',
    `created_at`         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_companion_session` (`id`),
    UNIQUE KEY `uk_companion_session_active` (`user_id`, (IF(`status` = 'ACTIVE', 1, NULL))),
    INDEX `idx_companion_session_pet` (`pet_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='陪伴会话(服务端计时权威,前端秒数仅参考)';

CREATE TABLE IF NOT EXISTS `pet_companion_daily` (
    `id`               BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `user_id`          BIGINT UNSIGNED NOT NULL COMMENT '用户ID(多端/多宠共享一份时间)',
    `business_date`    DATE NOT NULL COMMENT '业务日(businessZone=Asia/Shanghai)',
    `accepted_seconds` INT NOT NULL DEFAULT 0 COMMENT '当日有效陪伴秒数(服务端会话校验后累计,计入上限7200)',
    `granted_points`   INT NOT NULL DEFAULT 0 COMMENT '当日已发亲密度积分(上限8)',
    `created_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_companion_daily` (`id`),
    UNIQUE KEY `uk_companion_daily` (`user_id`, `business_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='陪伴业务日累计(同事务保存时长/积分/已发计数,跨日拆分)';
