-- =============================================
-- CloudMart 心愿宇宙模块 数据库迁移 V37
-- 模块: mall-wish
-- 说明: 全站虚拟礼物
--       1) wish_gift 礼物目录（管理后台维护：名称/图标/星光单价/上下架/排序）
--       2) wish_gift_record 送礼记录（发送方/接收方/场景对象/星光消耗快照）
--       3) 目录改名/改价不影响历史记录（快照字段），记录为消费凭证不可变更
-- =============================================

-- ---------------------------------------------
-- wish_gift 礼物目录
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_gift` (
    `id`              BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `name`            VARCHAR(50)  NOT NULL COMMENT '礼物名称',
    `icon_url`        VARCHAR(500) DEFAULT NULL COMMENT '礼物图标URL(空时前端展示默认礼物图标)',
    `animation_url`   VARCHAR(500) DEFAULT NULL COMMENT '礼物动效资源URL(可选,送出时播放)',
    `price_starlight` INT UNSIGNED NOT NULL COMMENT '单价(星光,正整数)',
    `status`          ENUM('ON_SHELF','OFF_SHELF') NOT NULL DEFAULT 'OFF_SHELF' COMMENT '状态:上架/下架(仅上架对用户可见)',
    `sort`            INT NOT NULL DEFAULT 0 COMMENT '排序值(越小越靠前)',
    `description`     VARCHAR(200) DEFAULT NULL COMMENT '礼物描述(可选)',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    `deleted_at`      DATETIME DEFAULT NULL COMMENT '软删时间(保留审计轨迹)',
    PRIMARY KEY `pk_wish_gift` (`id`),
    INDEX `idx_gift_status_sort` (`status`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='礼物目录(管理后台维护)';

-- ---------------------------------------------
-- wish_gift_record 送礼记录
-- 每次成功送礼插入一条；送礼为星光消费凭证，取消/退款不删除记录（历史事实）。
-- gift_name/gift_icon_url/unit_price 为送礼时点快照，目录后续变更不回溯。
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_gift_record` (
    `id`            BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `sender_id`     BIGINT UNSIGNED NOT NULL COMMENT '送礼人用户ID',
    `receiver_id`   BIGINT UNSIGNED NOT NULL COMMENT '收礼人用户ID',
    `gift_id`       BIGINT UNSIGNED NOT NULL COMMENT '礼物ID',
    `gift_name`     VARCHAR(50)  NOT NULL COMMENT '礼物名称快照',
    `gift_icon_url` VARCHAR(500) DEFAULT NULL COMMENT '礼物图标快照',
    `unit_price`    INT UNSIGNED NOT NULL COMMENT '送礼时单价快照(星光)',
    `count`         INT UNSIGNED NOT NULL COMMENT '数量(1-99)',
    `total_price`   INT UNSIGNED NOT NULL COMMENT '总消耗(星光)=unit_price*count',
    `target_type`   ENUM('WISH','POST','LIVE_ROOM') NOT NULL COMMENT '送礼场景:心愿/帖子/直播间',
    `target_id`     BIGINT UNSIGNED NOT NULL COMMENT '场景对象ID(心愿ID/帖子ID/直播间ID)',
    `message`       VARCHAR(100) DEFAULT NULL COMMENT '送礼留言(可选)',
    `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '送礼时间(UTC)',
    `updated_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    `deleted_at`    DATETIME DEFAULT NULL COMMENT '软删时间(管理端下架记录用)',
    PRIMARY KEY `pk_wish_gift_record` (`id`),
    INDEX `idx_gift_record_sender` (`sender_id`, `id`),
    INDEX `idx_gift_record_receiver` (`receiver_id`, `id`),
    INDEX `idx_gift_record_target` (`target_type`, `target_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='礼物赠送记录(星光消费凭证)';

-- ---------------------------------------------
-- 默认礼物种子目录（icon_url 置空，前端以默认礼物图标展示；管理后台可改价/换图/上下架）
-- ---------------------------------------------
INSERT INTO `wish_gift` (`id`, `name`, `icon_url`, `animation_url`, `price_starlight`, `status`, `sort`, `description`, `created_at`, `updated_at`) VALUES
(1949000000000000001, '小星星', NULL, NULL,  1, 'ON_SHELF', 1, '轻轻点亮一份心意', NOW(), NOW()),
(1949000000000000002, '爱心',   NULL, NULL,  5, 'ON_SHELF', 2, '为TA送上一个爱心', NOW(), NOW()),
(1949000000000000003, '咖啡',   NULL, NULL, 10, 'ON_SHELF', 3, '请TA喝一杯热咖啡', NOW(), NOW()),
(1949000000000000004, '香槟',   NULL, NULL, 30, 'ON_SHELF', 4, '值得庆祝的时刻', NOW(), NOW()),
(1949000000000000005, '皇冠',   NULL, NULL, 88, 'ON_SHELF', 5, '尊贵的心意', NOW(), NOW()),
(1949000000000000006, '火箭',   NULL, NULL, 199, 'ON_SHELF', 6, '一飞冲天的应援', NOW(), NOW())
ON DUPLICATE KEY UPDATE `updated_at` = `updated_at`;
