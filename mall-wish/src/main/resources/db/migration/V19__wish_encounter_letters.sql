-- =============================================
-- CloudMart 心愿宇宙模块 数据库迁移 V19
-- 模块: mall-wish
-- 说明: Sprint 3.3 擦肩而过（文档 2.10/十三/3.3/39.9）
--       wish_encounter_letter            相遇信笺（⑮b，deliver_after 延迟投递）
--       wish_encounter_letter_interaction 信笺匿名互动（㉚，每信笺每日 1 次）
--       wish_lbs_suspicious              位置伪造可疑记录
--       wish_lbs_freeze                  LBS 冻结（连续 3 次可疑 → 24h）
--       轨迹 wish_geo_trace 采用 Redis 存储（文档 39.x 首选：TTL 自动过期
--       替代 MySQL 逐行 DELETE；key lbs:trace:{bucket}:{geohash6} TTL 25h），
--       不建 MySQL 轨迹表——偏差留档进度文件四X。
-- ID策略: 雪花算法 (MyBatis-Plus assign_id), 不使用 AUTO_INCREMENT
-- 字符集: utf8mb4_0900_ai_ci, 引擎: InnoDB
-- =============================================

-- ---------------------------------------------
-- ⑮b wish_encounter_letter 相遇信笺
-- 一次匹配生成两条镜像信笺（互为 owner/peer）；status 状态机
-- PENDING(deliver_after 未到) → DELIVERED(已投递) → READ(已拆信)；
-- wish_tags 快照用于诗意文案；uk 防同对用户同格同桶重复生成。
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_encounter_letter` (
    `id`             BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `owner_user_id`  BIGINT UNSIGNED NOT NULL COMMENT '信笺归属用户',
    `peer_user_id`   BIGINT UNSIGNED NOT NULL COMMENT '对方用户(匿名展示,不外露)',
    `peer_wish_id`   BIGINT UNSIGNED NOT NULL COMMENT '对方心愿ID(点亮互动目标)',
    `geohash6`       CHAR(6) NOT NULL COMMENT '相遇网格(约1.2km)',
    `time_bucket`    DATETIME NOT NULL COMMENT '30 分钟时间桶(向下取整,UTC)',
    `wish_tags`      JSON DEFAULT NULL COMMENT '双方重叠心愿标签快照',
    `encounter_time` DATETIME NOT NULL COMMENT '相遇时刻(UTC,桶内实际时间)',
    `content`        VARCHAR(300) DEFAULT NULL COMMENT '诗意文案(PENDING 时对用户返回 null)',
    `status`         ENUM('PENDING','DELIVERED','READ') NOT NULL DEFAULT 'PENDING' COMMENT '状态机',
    `deliver_after`  DATETIME NOT NULL COMMENT '延迟投递时间(UTC,生成时随机 1-24h)',
    `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `delivered_at`   DATETIME DEFAULT NULL COMMENT '投递时间(UTC)',
    `read_at`        DATETIME DEFAULT NULL COMMENT '拆信时间(UTC)',
    PRIMARY KEY `pk_wish_encounter_letter` (`id`),
    UNIQUE KEY `uk_letter_pair` (`owner_user_id`, `peer_user_id`, `geohash6`, `time_bucket`),
    INDEX `idx_letter_owner` (`owner_user_id`, `status`, `deliver_after`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='相遇信笺(匿名,延迟投递)';

-- ---------------------------------------------
-- ㉚ wish_encounter_letter_interaction 信笺匿名互动
-- 复用 2.2 互动模型但独立表；uk(letter,user,互动日) = 单信笺每用户每日
-- 1 次；BLESS 免费 / LIGHT 扣星光 2 并点亮对方心愿。
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_encounter_letter_interaction` (
    `id`           BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `letter_id`    BIGINT UNSIGNED NOT NULL COMMENT '信笺ID',
    `user_id`      BIGINT UNSIGNED NOT NULL COMMENT '互动发起者(信笺归属人)',
    `type`         ENUM('BLESS','LIGHT') NOT NULL COMMENT '匿名祝福/点亮对方心愿',
    `peer_wish_id` BIGINT UNSIGNED NOT NULL COMMENT '对方心愿ID(LIGHT 时 support_count+1)',
    `interact_date` DATE NOT NULL COMMENT '互动日期(用户时区日,幂等键)',
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    PRIMARY KEY `pk_wish_encounter_letter_interaction` (`id`),
    UNIQUE KEY `uk_letter_interact_daily` (`letter_id`, `user_id`, `interact_date`),
    INDEX `idx_letter_interact_user` (`user_id`, `created_at`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='信笺匿名互动(祝福/点亮,每日1次)';

-- ---------------------------------------------
-- wish_lbs_suspicious 位置伪造可疑记录（异常跳跃判定）
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_lbs_suspicious` (
    `id`           BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `user_id`      BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `speed_kmh`    INT NOT NULL COMMENT '推断速度(km/h)',
    `from_cell`    CHAR(7) NOT NULL COMMENT '起点 geohash7(无原始坐标)',
    `to_cell`      CHAR(7) NOT NULL COMMENT '终点 geohash7(无原始坐标)',
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    PRIMARY KEY `pk_wish_lbs_suspicious` (`id`),
    INDEX `idx_suspicious_user` (`user_id`, `created_at`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='位置伪造可疑记录(异常跳跃)';

-- ---------------------------------------------
-- wish_lbs_freeze LBS 功能冻结（连续 3 次可疑 → 24h；管理台可解冻）
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_lbs_freeze` (
    `id`           BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `user_id`      BIGINT UNSIGNED NOT NULL COMMENT '冻结用户',
    `reason`       VARCHAR(100) DEFAULT NULL COMMENT '冻结原因',
    `frozen_until` DATETIME NOT NULL COMMENT '冻结截止(UTC)',
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_wish_lbs_freeze` (`id`),
    UNIQUE KEY `uk_lbs_freeze_user` (`user_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='LBS 功能冻结(24h,可解冻)';
