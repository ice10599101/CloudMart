-- =============================================
-- CloudMart 心愿宇宙模块 数据库迁移 V22
-- 模块: mall-wish
-- 说明: Sprint 3.6 虚拟收藏 + 品牌合作（文档 2.22/2.16/3.6）
--       wish_virtual_asset  ㉞ 资产配置表（配置表化，新增皮肤仅插入配置）
--       wish_user_asset     ㉟ 用户资产（uk_user_asset 防重复拥有）
--       wish_brand          品牌入驻（审核状态机）
--       wish_brand_pool     品牌许愿池（认领心愿分类）
--       wish_brand_pool_member  许愿池成员
--       皮肤/BGM 激活：wish_user_asset.is_active 标记（同类型互斥）
--       限量并发：Redis SETNX activity 语义（DECR 预扣库存）
--       RMB 内购：对接 mall-payment 支付通道，本期交付星光兑换闭环，
--       RMB 预留 paymentMethod 枚举与接口（偏差留档进度文件）
-- ID策略: 雪花算法 (MyBatis-Plus assign_id), 不使用 AUTO_INCREMENT
-- 字符集: utf8mb4_0900_ai_ci, 引擎: InnoDB
-- =============================================

-- ---------------------------------------------
-- ㉞ wish_virtual_asset 资产配置表
-- asset_type: SKIN 皮肤 / BGM 背景 / SPECIAL_FRUIT 星火收藏品；
-- pay_method: STARLIGHT / RMB / BOTH；stock 0=无限；valid_to 过期下架。
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_virtual_asset` (
    `id`               BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `asset_type`       ENUM('SKIN','BGM','SPECIAL_FRUIT') NOT NULL COMMENT '资产类型(徽章走 wish_badge 体系)',
    `name`             VARCHAR(60) NOT NULL COMMENT '资产名称',
    `description`      VARCHAR(300) DEFAULT NULL COMMENT '资产描述',
    `icon`             VARCHAR(500) DEFAULT NULL COMMENT '图标/预览 URL',
    `resource_url`     VARCHAR(500) DEFAULT NULL COMMENT '资源 URL(皮肤 CSS/BGM mp3)',
    `price_starlight`  INT NOT NULL DEFAULT 0 COMMENT '星光价格(0=不可星光兑换)',
    `price_rmb`        INT NOT NULL DEFAULT 0 COMMENT 'RMB 价格(分,0=不可内购)',
    `pay_method`       ENUM('STARLIGHT','RMB','BOTH') NOT NULL DEFAULT 'STARLIGHT' COMMENT '支付方式',
    `stock`            INT NOT NULL DEFAULT 0 COMMENT '限量库存(0=无限)',
    `valid_from`       DATETIME DEFAULT NULL COMMENT '上架开始(UTC,NULL=不限)',
    `valid_to`         DATETIME DEFAULT NULL COMMENT '下架时间(UTC,过期自动下架)',
    `is_active`        TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否上架',
    `created_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_wish_virtual_asset` (`id`),
    INDEX `idx_asset_type` (`asset_type`, `is_active`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='虚拟资产配置(皮肤/BGM/星火收藏品)';

-- ---------------------------------------------
-- ㉟ wish_user_asset 用户资产
-- uk(user_id, asset_id) 防重复拥有；is_active_skin/bgm 为用户当前激活
-- 的皮肤/BGM（同类型互斥，切换即时生效）；status REFUNDED = 已退款不可用。
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_user_asset` (
    `id`              BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `user_id`         BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `asset_id`        BIGINT UNSIGNED NOT NULL COMMENT '资产配置ID',
    `source`          ENUM('EXCHANGE','ACTIVITY','COLLECT') NOT NULL COMMENT '获取来源(星光兑换/活动/星火收藏)',
    `status`          ENUM('OWNED','REFUNDED') NOT NULL DEFAULT 'OWNED' COMMENT '资产状态',
    `is_active_skin`  TINYINT(1) NOT NULL DEFAULT 0 COMMENT '当前激活皮肤(同类型互斥)',
    `is_active_bgm`   TINYINT(1) NOT NULL DEFAULT 0 COMMENT '当前激活 BGM',
    `ref_wish_id`     BIGINT UNSIGNED DEFAULT NULL COMMENT '关联心愿(星火收藏品=被收藏的 SPARK 心愿)',
    `acquired_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '获取时间(UTC)',
    PRIMARY KEY `pk_wish_user_asset` (`id`),
    UNIQUE KEY `uk_user_asset` (`user_id`, `asset_id`),
    INDEX `idx_user_asset_user` (`user_id`, `status`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户虚拟资产(皮肤/BGM/星火收藏品)';

-- ---------------------------------------------
-- wish_brand 品牌（入驻审核状态机 PENDING→APPROVED/REJECTED）
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_brand` (
    `id`           BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `name`         VARCHAR(120) NOT NULL COMMENT '品牌名称',
    `logo`         VARCHAR(500) DEFAULT NULL COMMENT '品牌 Logo URL',
    `description`  VARCHAR(500) DEFAULT NULL COMMENT '品牌介绍',
    `category_id`  BIGINT UNSIGNED DEFAULT NULL COMMENT '认领心愿分类',
    `status`       ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING' COMMENT '入驻审核状态',
    `created_by`   BIGINT UNSIGNED NOT NULL COMMENT '入驻提交人',
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_wish_brand` (`id`),
    INDEX `idx_brand_status` (`status`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='品牌(入驻审核)';

-- ---------------------------------------------
-- wish_brand_pool 品牌许愿池（认领心愿分类 → 用户加入 → 达成发奖）
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_brand_pool` (
    `id`             BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `brand_id`       BIGINT UNSIGNED NOT NULL COMMENT '品牌ID',
    `category_id`    BIGINT UNSIGNED NOT NULL COMMENT '认领心愿分类(关联 wish_category)',
    `name`           VARCHAR(120) NOT NULL COMMENT '许愿池名称',
    `target_count`   INT NOT NULL COMMENT '目标人数',
    `current_count`  INT NOT NULL DEFAULT 0 COMMENT '当前参与人数',
    `reward_json`    JSON DEFAULT NULL COMMENT '达成奖励 JSON(如 {"starlight":50})',
    `end_at`         DATETIME DEFAULT NULL COMMENT '结束时间(UTC)',
    `status`         ENUM('ACTIVE','ENDED') NOT NULL DEFAULT 'ACTIVE' COMMENT '池状态',
    `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_wish_brand_pool` (`id`),
    INDEX `idx_pool_brand` (`brand_id`, `status`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='品牌许愿池';

-- ---------------------------------------------
-- wish_brand_pool_member 许愿池成员（uk 池×用户 防重复加入）
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_brand_pool_member` (
    `id`         BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `pool_id`    BIGINT UNSIGNED NOT NULL COMMENT '许愿池ID',
    `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '加入用户',
    `joined_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间(UTC)',
    PRIMARY KEY `pk_wish_brand_pool_member` (`id`),
    UNIQUE KEY `uk_pool_member` (`pool_id`, `user_id`),
    INDEX `idx_pool_member_user` (`user_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='品牌许愿池成员';
