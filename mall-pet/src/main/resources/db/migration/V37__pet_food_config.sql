-- F1 偏差修正：食物道具配置化——效果/价格从代码常量迁移到配置表（管理端可编辑，服务端权威）。
-- 原 PetItemCatalog.FOODS 常量数据作为种子落库；管理端保存后本实例即时生效，其他实例 ≤60 秒（TTL 缓存）。

CREATE TABLE IF NOT EXISTS `pet_food_config` (
    `id`              BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `code`            VARCHAR(32) NOT NULL COMMENT '食物编码(唯一,喂食接口入参)',
    `name`            VARCHAR(32) NOT NULL COMMENT '名称',
    `icon`            VARCHAR(8) NOT NULL DEFAULT '🍎' COMMENT '图标emoji',
    `description`     VARCHAR(64) NOT NULL DEFAULT '' COMMENT '描述',
    `price_starlight` INT NOT NULL DEFAULT 0 COMMENT '售价(星光,≥0)',
    `hunger`          INT NOT NULL DEFAULT 0 COMMENT '饱食恢复(0~100)',
    `happiness`       INT NOT NULL DEFAULT 0 COMMENT '心情恢复(0~100)',
    `hp`              INT NOT NULL DEFAULT 0 COMMENT '生命恢复(0~10000,受max_hp封顶)',
    `enabled`         TINYINT NOT NULL DEFAULT 1 COMMENT '上架: 1上架 0下架',
    `sort`            INT NOT NULL DEFAULT 0 COMMENT '商城排序',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_food_config` (`id`),
    UNIQUE KEY `uk_food_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物食物道具配置(商城+喂养效果)';

INSERT INTO `pet_food_config` (`id`, `code`, `name`, `icon`, `description`, `price_starlight`, `hunger`, `happiness`, `hp`, `sort`) VALUES
    (1940000000000000001, 'apple', '苹果', '🍎', '脆脆的苹果，宠物最爱', 20, 15, 2, 0, 1),
    (1940000000000000002, 'milk',  '牛奶', '🥛', '温热的一杯牛奶', 25, 10, 8, 5, 2),
    (1940000000000000003, 'fish',  '小鱼干', '🐟', '香喷喷的小鱼干', 35, 25, 5, 10, 3),
    (1940000000000000004, 'cake', '奶油蛋糕', '🍰', '节日限定的甜品', 60, 40, 12, 15, 4);
