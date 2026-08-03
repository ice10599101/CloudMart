-- CloudMart Community 社区模块数据库初始化

CREATE TABLE IF NOT EXISTS `posts` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '作者用户ID',
    `title` VARCHAR(200) NOT NULL COMMENT '标题',
    `content` LONGTEXT NOT NULL COMMENT '内容(富文本HTML)',
    `cover_image` VARCHAR(500) DEFAULT NULL COMMENT '封面图URL',
    `media_urls` JSON DEFAULT NULL COMMENT '媒体资源URL列表',
    `media_type` VARCHAR(10) NOT NULL DEFAULT 'IMAGE' COMMENT '媒体类型: IMAGE/VIDEO/MIXED',
    `category_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联分类ID',
    `product_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联商品ID',
    `like_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '点赞数',
    `comment_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '评论数',
    `collect_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '收藏数',
    `share_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '分享数',
    `view_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '浏览数',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-草稿 1-已发布 2-隐藏 3-管理员删除',
    `is_top` TINYINT NOT NULL DEFAULT 0 COMMENT '是否置顶: 0-否 1-是',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted_at` DATETIME DEFAULT NULL COMMENT '软删除时间',
    PRIMARY KEY `pk_posts` (`id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_status_created` (`status`, `created_at`),
    INDEX `idx_category_id` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='社区帖子表';

CREATE TABLE IF NOT EXISTS `post_comments` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `post_id` BIGINT UNSIGNED NOT NULL COMMENT '帖子ID',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '评论用户ID',
    `parent_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '父评论ID',
    `reply_to_user_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '回复目标用户ID',
    `content` VARCHAR(1000) NOT NULL COMMENT '评论内容',
    `like_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '点赞数',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0-正常 1-隐藏 2-管理员删除',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted_at` DATETIME DEFAULT NULL COMMENT '软删除时间',
    PRIMARY KEY `pk_post_comments` (`id`),
    INDEX `idx_post_id` (`post_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='帖子评论表';

CREATE TABLE IF NOT EXISTS `post_likes` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `target_type` VARCHAR(10) NOT NULL COMMENT '目标类型: POST/COMMENT',
    `target_id` BIGINT UNSIGNED NOT NULL COMMENT '目标ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY `pk_post_likes` (`id`),
    UNIQUE KEY `uk_user_target` (`user_id`, `target_type`, `target_id`),
    INDEX `idx_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='帖子/评论点赞表';

CREATE TABLE IF NOT EXISTS `post_collections` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `post_id` BIGINT UNSIGNED NOT NULL COMMENT '帖子ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY `pk_post_collections` (`id`),
    UNIQUE KEY `uk_user_post` (`user_id`, `post_id`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='帖子收藏表';

CREATE TABLE IF NOT EXISTS `user_follows` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `follower_id` BIGINT UNSIGNED NOT NULL COMMENT '关注者ID',
    `following_id` BIGINT UNSIGNED NOT NULL COMMENT '被关注者ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY `pk_user_follows` (`id`),
    UNIQUE KEY `uk_follower_following` (`follower_id`, `following_id`),
    INDEX `idx_following_id` (`following_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户关注关系表';

CREATE TABLE IF NOT EXISTS `tags` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name` VARCHAR(50) NOT NULL COMMENT '标签名',
    `icon` VARCHAR(255) DEFAULT NULL COMMENT '图标URL',
    `post_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '帖子数',
    `is_hot` TINYINT NOT NULL DEFAULT 0 COMMENT '是否热门: 0-否 1-是',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用 1-启用',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY `pk_tags` (`id`),
    UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='话题标签表';

CREATE TABLE IF NOT EXISTS `post_tags` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `post_id` BIGINT UNSIGNED NOT NULL COMMENT '帖子ID',
    `tag_id` BIGINT UNSIGNED NOT NULL COMMENT '标签ID',
    PRIMARY KEY `pk_post_tags` (`id`),
    UNIQUE KEY `uk_post_tag` (`post_id`, `tag_id`),
    INDEX `idx_tag_id` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='帖子-标签关联表';

CREATE TABLE IF NOT EXISTS `reports` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `reporter_id` BIGINT UNSIGNED NOT NULL COMMENT '举报人ID',
    `target_type` VARCHAR(10) NOT NULL COMMENT '目标类型: POST/COMMENT/USER',
    `target_id` BIGINT UNSIGNED NOT NULL COMMENT '目标ID',
    `reason` VARCHAR(50) NOT NULL COMMENT '举报原因',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '详细描述',
    `images` JSON DEFAULT NULL COMMENT '截图URL列表',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0-待处理 1-处理中 2-驳回 3-已处理',
    `handler_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '处理人ID',
    `handle_note` VARCHAR(500) DEFAULT NULL COMMENT '处理备注',
    `handled_at` DATETIME DEFAULT NULL COMMENT '处理时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY `pk_reports` (`id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_target` (`target_type`, `target_id`),
    INDEX `idx_reporter_id` (`reporter_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='内容举报表';

CREATE TABLE IF NOT EXISTS `badges` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name` VARCHAR(50) NOT NULL COMMENT '徽章名称',
    `icon` VARCHAR(255) NOT NULL COMMENT '徽章图标URL',
    `description` VARCHAR(200) NOT NULL COMMENT '徽章描述',
    `condition` JSON DEFAULT NULL COMMENT '获得条件',
    `level` TINYINT NOT NULL DEFAULT 1 COMMENT '等级',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用 1-启用',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY `pk_badges` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='成就徽章表';

CREATE TABLE IF NOT EXISTS `user_badges` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `badge_id` BIGINT UNSIGNED NOT NULL COMMENT '徽章ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY `pk_user_badges` (`id`),
    UNIQUE KEY `uk_user_badge` (`user_id`, `badge_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户-徽章关联表';
