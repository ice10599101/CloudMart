-- CloudMart WMS 仓储模块数据库完善
-- 拣货作业单表
CREATE TABLE IF NOT EXISTS `pick_orders` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_id` BIGINT UNSIGNED NOT NULL COMMENT '关联订单ID',
    `warehouse_id` BIGINT UNSIGNED NOT NULL COMMENT '仓库ID',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING-待拣货/PICKING-拣货中/PICKED-已拣货/PACKED-已打包/SHIPPED-已发货',
    `assigned_user_id` BIGINT UNSIGNED COMMENT '分配的拣货员ID',
    `pick_time` DATETIME COMMENT '开始拣货时间',
    `packed_time` DATETIME COMMENT '打包完成时间',
    `remark` VARCHAR(500) COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY `pk_pick_orders` (`id`),
    INDEX `idx_pick_orders_order` (`order_id`),
    INDEX `idx_pick_orders_warehouse` (`warehouse_id`),
    INDEX `idx_pick_orders_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='拣货作业单表';

-- 拣货明细表
CREATE TABLE IF NOT EXISTS `pick_order_items` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `pick_order_id` BIGINT UNSIGNED NOT NULL COMMENT '拣货单ID',
    `sku_id` BIGINT UNSIGNED NOT NULL COMMENT 'SKU ID',
    `product_name` VARCHAR(256) NOT NULL COMMENT '商品名称',
    `sku_attributes` VARCHAR(256) COMMENT 'SKU属性',
    `quantity` INT NOT NULL COMMENT '数量',
    `location_code` VARCHAR(64) COMMENT '库位编码',
    `picked_quantity` INT NOT NULL DEFAULT 0 COMMENT '已拣数量',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY `pk_pick_order_items` (`id`),
    INDEX `idx_pick_order_items_pick` (`pick_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='拣货明细表';

-- 入库单表
CREATE TABLE IF NOT EXISTS `inbound_orders` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `warehouse_id` BIGINT UNSIGNED NOT NULL COMMENT '仓库ID',
    `type` VARCHAR(20) NOT NULL DEFAULT 'PURCHASE' COMMENT '类型: PURCHASE-采购入库/RETURN-退货入库/TRANSFER-调拨入库',
    `reference_no` VARCHAR(64) COMMENT '关联单号（采购单号/退货单号）',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING-待入库/RECEIVING-入库中/COMPLETED-已完成',
    `total_quantity` INT NOT NULL DEFAULT 0 COMMENT '总数量',
    `received_quantity` INT NOT NULL DEFAULT 0 COMMENT '已收数量',
    `operator_user_id` BIGINT UNSIGNED COMMENT '操作员ID',
    `completed_time` DATETIME COMMENT '完成时间',
    `remark` VARCHAR(500) COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY `pk_inbound_orders` (`id`),
    INDEX `idx_inbound_orders_warehouse` (`warehouse_id`),
    INDEX `idx_inbound_orders_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='入库单表';

-- 入库明细表
CREATE TABLE IF NOT EXISTS `inbound_order_items` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `inbound_order_id` BIGINT UNSIGNED NOT NULL COMMENT '入库单ID',
    `sku_id` BIGINT UNSIGNED NOT NULL COMMENT 'SKU ID',
    `product_name` VARCHAR(256) NOT NULL COMMENT '商品名称',
    `expected_quantity` INT NOT NULL COMMENT '预期数量',
    `received_quantity` INT NOT NULL DEFAULT 0 COMMENT '实际收货数量',
    `location_code` VARCHAR(64) COMMENT '入库库位',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY `pk_inbound_order_items` (`id`),
    INDEX `idx_inbound_order_items_inbound` (`inbound_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='入库明细表';
