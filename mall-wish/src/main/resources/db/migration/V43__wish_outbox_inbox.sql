-- V43: 可靠事件 outbox / 消费去重 inbox（B13，任务书 §6.3）
-- 背景：WishStatEventProducer 发送失败仅记日志、afterCommit+内存异步不保证宕机不丢；
--      消费端 WishStatSyncConsumer 每次消费都累计 totalHelped（影响徽章/等级），
--      消息重复投递会重复计数。
-- 方案：业务事实与 outbox 同事务落库，中继任务租约投递 RocketMQ（退避 1s/5s/30s/2min/10min，
--      超 10 次进入 DEAD 告警）；消费端以 (consumer_name, event_id) 与本地副作用同事务去重。

CREATE TABLE IF NOT EXISTS `wish_outbox` (
    `event_id`        VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '全局事件ID(UUID)',
    `aggregate_type`  VARCHAR(40) NOT NULL COMMENT '聚合类型:WISH/FULFILLMENT/WALLET',
    `aggregate_id`    BIGINT UNSIGNED NOT NULL COMMENT '聚合ID',
    `aggregate_version` BIGINT NOT NULL DEFAULT 0 COMMENT '聚合版本(事件排序与幂等)',
    `event_type`      VARCHAR(64) NOT NULL COMMENT '事件类型:WishFulfilled/WishVisibilityChanged等',
    `payload`         JSON DEFAULT NULL COMMENT '最小载荷(ID/版本/展示字段;私密正文不进总线)',
    `status`          ENUM('PENDING','PUBLISHED','DEAD') NOT NULL DEFAULT 'PENDING' COMMENT '投递状态',
    `attempts`        INT NOT NULL DEFAULT 0 COMMENT '已尝试次数',
    `next_attempt_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '下次可投递时间',
    `lease_owner`     VARCHAR(64) DEFAULT NULL COMMENT '租约持有者(实例标识)',
    `lease_until`     DATETIME(3) DEFAULT NULL COMMENT '租约到期时间',
    `created_at`      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
    `published_at`    DATETIME(3) DEFAULT NULL COMMENT '投递成功时间(UTC)',
    PRIMARY KEY `pk_wish_outbox` (`event_id`),
    INDEX `idx_wish_outbox_status` (`status`, `next_attempt_at`, `id`),
    INDEX `idx_wish_outbox_aggregate` (`aggregate_type`, `aggregate_id`, `aggregate_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='事务性发件箱(业务事实与事件原子提交)';

CREATE TABLE IF NOT EXISTS `wish_event_inbox` (
    `consumer_name` VARCHAR(64) NOT NULL COMMENT '消费者标识(每个消费逻辑一个)',
    `event_id`      VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '全局事件ID',
    `processed_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '处理时间(UTC)',
    PRIMARY KEY `pk_wish_event_inbox` (`consumer_name`, `event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消费端事件去重(与本地副作用同事务写入)';
