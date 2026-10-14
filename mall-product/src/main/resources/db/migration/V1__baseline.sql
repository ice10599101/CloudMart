-- C26 clean development/test baseline. Recreated EMPTY databases only.
-- Materialized from verified MySQL schema; never repair an old history to adopt this file.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE `brands` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '品牌ID',
  `name` varchar(100) NOT NULL COMMENT '品牌名称',
  `logo` varchar(255) DEFAULT NULL COMMENT '品牌Logo URL',
  `description` varchar(500) DEFAULT NULL COMMENT '品牌描述',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序值',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 0-禁用, 1-正常',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='品牌表';

CREATE TABLE `categories` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `name` varchar(100) NOT NULL COMMENT '分类名称',
  `parent_id` bigint unsigned DEFAULT '0' COMMENT '父分类ID(0为顶级)',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序值',
  `icon` varchar(255) DEFAULT NULL COMMENT '分类图标URL',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 0-禁用, 1-正常',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品分类表';

CREATE TABLE `product_question` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `product_id` bigint unsigned NOT NULL COMMENT '商品 ID',
  `user_id` bigint unsigned NOT NULL COMMENT '提问人用户 ID',
  `question` varchar(500) NOT NULL COMMENT '问题内容',
  `answer` varchar(2000) DEFAULT NULL COMMENT '回答内容（可空）',
  `answer_user_id` bigint unsigned DEFAULT NULL COMMENT '回答人用户 ID（任意登录用户可答，前端标注已购身份）',
  `answer_purchased` tinyint(1) NOT NULL DEFAULT '0' COMMENT '回答人是否已购该商品（回答时快照）',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0 正常 / 1 隐藏',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `answered_at` datetime DEFAULT NULL COMMENT '回答时间',
  PRIMARY KEY (`id`),
  KEY `idx_product_status` (`product_id`,`status`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品问答（N-5 问大家）';

CREATE TABLE `product_reviews` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '评价ID',
  `product_id` bigint unsigned NOT NULL COMMENT '商品ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `order_id` bigint unsigned NOT NULL COMMENT '订单ID',
  `order_item_id` bigint unsigned DEFAULT NULL COMMENT 'R08 订单行锚点（order_items.id）',
  `sku_id` bigint unsigned NOT NULL COMMENT 'SKU ID',
  `rating` tinyint unsigned NOT NULL COMMENT '评分(1-5)',
  `content` varchar(1000) NOT NULL COMMENT '评价内容',
  `images` varchar(2000) DEFAULT NULL COMMENT '评价图片(JSON数组)',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 0-隐藏, 1-显示',
  `hide_reason` varchar(200) DEFAULT NULL COMMENT 'R08 管理员下架/驳回理由（作者可见）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_order_item` (`user_id`,`order_item_id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_order_item_id` (`order_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品评价表';

CREATE TABLE `product_reward_obligation` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `biz_key` varchar(96) NOT NULL COMMENT '业务幂等键（REVIEW_REWARD:{reviewId}，uk）',
  `source` varchar(24) NOT NULL COMMENT 'REVIEW_REWARD',
  `source_biz_id` varchar(64) NOT NULL COMMENT '来源事实 ID（评价 ID）',
  `beneficiary` bigint unsigned NOT NULL COMMENT '受益人用户 ID',
  `reward_type` varchar(24) NOT NULL COMMENT 'STARLIGHT',
  `amount` int NOT NULL COMMENT '数量',
  `description` varchar(200) DEFAULT NULL COMMENT '备注',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SETTLED/REJECTED',
  `attempts` int NOT NULL DEFAULT '0' COMMENT '已尝试次数',
  `last_error` varchar(300) DEFAULT NULL COMMENT '最近错误',
  `next_retry_at` datetime DEFAULT NULL COMMENT '下次重试时间（退避）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `settled_at` datetime DEFAULT NULL COMMENT '交付完成时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_key` (`biz_key`),
  KEY `idx_status_retry` (`status`,`next_retry_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评价奖励交付义务（R07）';

CREATE TABLE `product_skus` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'SKU ID',
  `product_id` bigint unsigned NOT NULL COMMENT '商品ID',
  `sku_code` varchar(50) NOT NULL COMMENT 'SKU编码',
  `attributes` varchar(500) DEFAULT NULL COMMENT 'SKU属性(JSON)',
  `price` decimal(10,2) NOT NULL COMMENT '售价',
  `original_price` decimal(10,2) DEFAULT NULL COMMENT '原价',
  `stock` int unsigned NOT NULL DEFAULT '0' COMMENT '库存数量',
  `image` varchar(255) DEFAULT NULL COMMENT 'SKU图片URL',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 0-禁用, 1-正常',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sku_code` (`sku_code`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品SKU表';

CREATE TABLE `products` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '商品ID',
  `name` varchar(200) NOT NULL COMMENT '商品名称',
  `description` text COMMENT '商品描述',
  `category_id` bigint unsigned NOT NULL COMMENT '分类ID',
  `brand_id` bigint unsigned DEFAULT NULL COMMENT '品牌ID',
  `brand` varchar(100) DEFAULT NULL COMMENT '品牌',
  `main_image` varchar(255) DEFAULT NULL COMMENT '主图URL',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 0-下架, 1-上架',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` datetime DEFAULT NULL COMMENT '删除时间(软删除)',
  PRIMARY KEY (`id`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_status` (`status`),
  KEY `idx_brand_id` (`brand_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品表';

CREATE TABLE `wishlists` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '收藏ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `product_id` bigint unsigned NOT NULL COMMENT '商品ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product` (`user_id`,`product_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品收藏表';
