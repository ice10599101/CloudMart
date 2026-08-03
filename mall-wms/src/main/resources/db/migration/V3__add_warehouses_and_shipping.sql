CREATE TABLE IF NOT EXISTS `warehouses` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name` VARCHAR(100) NOT NULL COMMENT '仓库名称',
    `address` VARCHAR(500) DEFAULT NULL COMMENT '仓库地址',
    `contact_phone` VARCHAR(20) DEFAULT NULL COMMENT '联系电话',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用, 1-正常',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY `pk_warehouses` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='仓库表';

CREATE TABLE IF NOT EXISTS `shipping_orders` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_id` BIGINT UNSIGNED NOT NULL COMMENT '关联订单ID',
    `warehouse_id` BIGINT UNSIGNED NOT NULL COMMENT '仓库ID',
    `shipping_no` VARCHAR(64) DEFAULT NULL COMMENT '物流单号',
    `carrier` VARCHAR(50) DEFAULT NULL COMMENT '承运商',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING-待发货/SHIPPED-已发货/DELIVERED-已签收',
    `receiver_name` VARCHAR(50) DEFAULT NULL COMMENT '收件人姓名',
    `receiver_phone` VARCHAR(20) DEFAULT NULL COMMENT '收件人电话',
    `receiver_address` VARCHAR(500) DEFAULT NULL COMMENT '收件人地址',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY `pk_shipping_orders` (`id`),
    INDEX `idx_shipping_orders_order` (`order_id`),
    INDEX `idx_shipping_orders_warehouse` (`warehouse_id`),
    INDEX `idx_shipping_orders_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物流订单表';

CREATE TABLE IF NOT EXISTS `shipping_trackings` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `shipping_order_id` BIGINT UNSIGNED NOT NULL COMMENT '物流订单ID',
    `location` VARCHAR(200) DEFAULT NULL COMMENT '所在地点',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '物流描述',
    `happened_at` DATETIME NOT NULL COMMENT '发生时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY `pk_shipping_trackings` (`id`),
    INDEX `idx_shipping_trackings_order` (`shipping_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物流轨迹表';
