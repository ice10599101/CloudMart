-- V24: B01 状态并发与拜访统一（BE-06/BE-03，§3.3）
-- pet_user_guard：用户级写锁（长期互斥/日额度组合/陪伴会话等写操作先锁该行，
--   统一锁顺序：user guard → 活动/订单 → pet(升序) → 钱包 → 库存/进度）。
-- pet_visit_fact：三类拜访入口（串门/家园/好友互访）共享的数据库事实——
--   唯一 (visitor, owner, businessDate)：不同宠物同主人仍只算一次；
--   是否有收益由 PetQuotaService（数据库权威额度）裁决，Redis 不再参与资格判定（BE-06）。

CREATE TABLE IF NOT EXISTS `pet_user_guard` (
    `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '用户ID(主键即锁粒度)',
    `version`    BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '守卫版本(保留列,当前仅作行锁载体)',
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_user_guard` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户级写锁(并发写路径先 SELECT FOR UPDATE 本行)';

CREATE TABLE IF NOT EXISTS `pet_visit_fact` (
    `id`              BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `visitor_user_id` BIGINT UNSIGNED NOT NULL COMMENT '拜访者用户ID',
    `owner_user_id`   BIGINT UNSIGNED NOT NULL COMMENT '被访主人用户ID',
    `visitor_pet_id`  BIGINT UNSIGNED NOT NULL COMMENT '拜访宠物ID(事实冻结归属)',
    `owner_pet_id`    BIGINT UNSIGNED NOT NULL COMMENT '被访宠物ID',
    `source`          ENUM('NEIGHBOR','ROOM','FRIEND') NOT NULL COMMENT '入口:串门/家园/好友互访',
    `business_date`   DATE NOT NULL COMMENT '业务日(Asia/Shanghai,PetClock 口径)',
    `reward_granted`  TINYINT NOT NULL DEFAULT 0 COMMENT '本次是否有收益(额度裁决;无收益拜访仍成立)',
    `created_at`      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC)',
    PRIMARY KEY `pk_pet_visit_fact` (`id`),
    UNIQUE KEY `uk_pet_visit_fact` (`visitor_user_id`, `owner_user_id`, `business_date`),
    INDEX `idx_pet_visit_fact_visitor_date` (`visitor_user_id`, `business_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='拜访事实(三入口统一;同主人同业务日至多一次;收益资格由数据库额度裁决)';
