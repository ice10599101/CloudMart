-- =============================================
-- CloudMart 心愿宇宙模块 数据库迁移 V18
-- 模块: mall-wish
-- 说明: Sprint 3.2 城市幸福地图 + 地理围栏（文档 2.10/十二/3.2）
--       wish_warm_event  UGC 温暖事件（模糊化坐标，DFA 敏感词审核）
--       wish_fence       地理围栏（服务端存储围栏中心，客户端不可见）
--       wish_fence_arrival  围栏到达记录（幂等：每围栏每用户每日一次）
-- ID策略: 雪花算法 (MyBatis-Plus assign_id), 不使用 AUTO_INCREMENT
-- 字符集: utf8mb4_0900_ai_ci, 引擎: InnoDB
-- =============================================

-- ---------------------------------------------
-- wish_warm_event 温暖事件（城市幸福地图 UGC：小店老板送咖啡等）
-- 坐标仅存 geohash7（约 153m 网格）；city_code = geohash4 城市代理
-- （同 3.1 口径）；DFA 命中敏感词 → AUTO_HIDDEN，未命中 → PENDING 先发后审
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_warm_event` (
    `id`           BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `user_id`      BIGINT UNSIGNED NOT NULL COMMENT '发布者用户ID',
    `title`        VARCHAR(60) NOT NULL COMMENT '事件标题(≤60字)',
    `content`      VARCHAR(500) NOT NULL COMMENT '事件内容(≤500字)',
    `geohash`      CHAR(7) DEFAULT NULL COMMENT 'geohash7 模糊化坐标(约153m网格,无原始坐标)',
    `city_code`    CHAR(4) DEFAULT NULL COMMENT '城市代理(geohash4前缀)',
    `audit_status` ENUM('PENDING','APPROVED','REJECTED','AUTO_HIDDEN') NOT NULL DEFAULT 'PENDING' COMMENT '审核状态(DFA命中→AUTO_HIDDEN)',
    `is_visible`   TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否可见(先发后审)',
    `deleted_at`   DATETIME DEFAULT NULL COMMENT '软删除时间',
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_wish_warm_event` (`id`),
    INDEX `idx_warm_geo` (`is_visible`, `audit_status`, `geohash`),
    INDEX `idx_warm_user` (`user_id`, `created_at`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='温暖事件(城市幸福地图UGC)';

-- ---------------------------------------------
-- ⑯ wish_fence 地理围栏（管理员配置）
-- center_geohash 为围栏中心（geohash7，约153m精度——半径≥10m 场景足够；
-- 围栏坐标仅服务端存储，用户端 API 永不回传——隐私验收）；半径最小 10m；
-- valid_from/valid_to 为生效周期（UTC，NULL=不限）。
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_fence` (
    `id`             BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `name`           VARCHAR(60) NOT NULL COMMENT '围栏名称(如"老城书店")',
    `wish_id`        BIGINT UNSIGNED NOT NULL COMMENT '到达后触发绽放的心愿ID',
    `center_geohash` CHAR(7) NOT NULL COMMENT '围栏中心 geohash7(服务端存储,客户端不可见)',
    `radius_m`       INT NOT NULL COMMENT '半径(米,最小10)',
    `valid_from`     DATETIME DEFAULT NULL COMMENT '生效开始(UTC,NULL=不限)',
    `valid_to`       DATETIME DEFAULT NULL COMMENT '生效结束(UTC,NULL=不限)',
    `is_active`      TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    `created_by`     BIGINT UNSIGNED NOT NULL COMMENT '创建管理员(管理后台用户ID)',
    `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_wish_fence` (`id`),
    INDEX `idx_fence_wish` (`wish_id`, `is_active`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='地理围栏(到达触发心愿绽放,坐标不回传)';

-- ---------------------------------------------
-- wish_fence_arrival 围栏到达记录（幂等：uk 围栏×用户×日）
-- 每用户每围栏每日至多触发一次"到达"（防重复刷绽放）。
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_fence_arrival` (
    `id`           BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `fence_id`     BIGINT UNSIGNED NOT NULL COMMENT '围栏ID',
    `user_id`      BIGINT UNSIGNED NOT NULL COMMENT '到达用户',
    `wish_id`      BIGINT UNSIGNED NOT NULL COMMENT '触发绽放的心愿ID',
    `checkin_date` DATE NOT NULL COMMENT '到达日期(用户时区日)',
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    PRIMARY KEY `pk_wish_fence_arrival` (`id`),
    UNIQUE KEY `uk_arrival_daily` (`fence_id`, `user_id`, `checkin_date`),
    INDEX `idx_arrival_user` (`user_id`, `created_at`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='围栏到达记录(幂等防刷)';
