-- CloudMart Marketing 模块数据库初始化
-- 拼团活动表
CREATE TABLE IF NOT EXISTS `group_activities` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name` VARCHAR(128) NOT NULL COMMENT '活动名称',
    `description` TEXT COMMENT '活动描述',
    `product_id` BIGINT UNSIGNED NOT NULL COMMENT '关联商品ID',
    `sku_id` BIGINT UNSIGNED NOT NULL COMMENT '关联SKU ID',
    `original_price` DECIMAL(10,2) NOT NULL COMMENT '原价',
    `group_price` DECIMAL(10,2) NOT NULL COMMENT '拼团价',
    `target_number` INT NOT NULL COMMENT '成团所需人数',
    `max_groups` INT NOT NULL DEFAULT 0 COMMENT '最大开团数，0=不限',
    `current_groups` INT NOT NULL DEFAULT 0 COMMENT '当前已开团数',
    `per_user_limit` INT NOT NULL DEFAULT 1 COMMENT '每人限参团数',
    `start_time` DATETIME NOT NULL COMMENT '活动开始时间',
    `end_time` DATETIME NOT NULL COMMENT '活动结束时间',
    `status` VARCHAR(20) NOT NULL DEFAULT 'DISABLED' COMMENT '状态: ENABLED/DISABLED/ENDED',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY `pk_group_activities` (`id`),
    INDEX `idx_group_activities_product` (`product_id`),
    INDEX `idx_group_activities_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='拼团活动表';

-- 拼团组表（每个开团对应一条记录）
CREATE TABLE IF NOT EXISTS `group_orders` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `activity_id` BIGINT UNSIGNED NOT NULL COMMENT '拼团活动ID',
    `leader_user_id` BIGINT UNSIGNED NOT NULL COMMENT '团长用户ID',
    `current_number` INT NOT NULL DEFAULT 1 COMMENT '当前参与人数',
    `target_number` INT NOT NULL COMMENT '成团所需人数',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING-拼团中/SUCCESS-已成团/FAILED-已失败/EXPIRED-已过期',
    `expire_time` DATETIME NOT NULL COMMENT '拼团过期时间',
    `success_time` DATETIME COMMENT '成团时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY `pk_group_orders` (`id`),
    INDEX `idx_group_orders_activity` (`activity_id`),
    INDEX `idx_group_orders_leader` (`leader_user_id`),
    INDEX `idx_group_orders_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='拼团组表';

-- 拼团参与记录表
CREATE TABLE IF NOT EXISTS `group_members` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `group_order_id` BIGINT UNSIGNED NOT NULL COMMENT '拼团组ID',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '参与用户ID',
    `activity_id` BIGINT UNSIGNED NOT NULL COMMENT '拼团活动ID',
    `is_leader` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否团长: 0-否 1-是',
    `order_id` BIGINT UNSIGNED COMMENT '关联订单ID（成团后生成）',
    `status` VARCHAR(20) NOT NULL DEFAULT 'JOINED' COMMENT '状态: JOINED-已加入/CONFIRMED-已确认/REFUNDED-已退款',
    `joined_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY `pk_group_members` (`id`),
    UNIQUE INDEX `uk_group_members_user_group` (`user_id`, `group_order_id`),
    INDEX `idx_group_members_group` (`group_order_id`),
    INDEX `idx_group_members_activity` (`activity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='拼团参与记录表';

-- 阶梯满减活动表
CREATE TABLE IF NOT EXISTS `tiered_promotions` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name` VARCHAR(128) NOT NULL COMMENT '活动名称',
    `description` TEXT COMMENT '活动描述',
    `start_time` DATETIME NOT NULL COMMENT '活动开始时间',
    `end_time` DATETIME NOT NULL COMMENT '活动结束时间',
    `status` VARCHAR(20) NOT NULL DEFAULT 'DISABLED' COMMENT '状态: ENABLED/DISABLED/ENDED',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY `pk_tiered_promotions` (`id`),
    INDEX `idx_tiered_promotions_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='阶梯满减活动表';

-- 阶梯满减规则表
CREATE TABLE IF NOT EXISTS `tiered_promotion_rules` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `promotion_id` BIGINT UNSIGNED NOT NULL COMMENT '满减活动ID',
    `min_amount` DECIMAL(10,2) NOT NULL COMMENT '最低消费金额',
    `discount_amount` DECIMAL(10,2) NOT NULL COMMENT '优惠金额',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY `pk_tiered_promotion_rules` (`id`),
    INDEX `idx_tiered_promotion_rules_promotion` (`promotion_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='阶梯满减规则表';
