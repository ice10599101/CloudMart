-- V38: 宠物星光交易业务操作幂等表（B01 星光交易幂等、结算记录与补偿）
-- 背景：wish_resource_log.ref_id 无唯一约束，/internal/pet-support/starlight/* 重复调用
--      （Feign 超时重试、消息重复、并发提交）会重复扣款/重复入账。
-- 方案：钱包端按业务操作唯一键（operation_id）原子去重——去重记录与余额更新同事务提交；
--      重复相同请求返回原结果；重复键但用户/金额/类型不同拒绝（WISH_OPERATION_CONFLICT）。
--      仅 COMPLETED 结果落库（失败事务整体回滚，可安全原单重试）。

CREATE TABLE IF NOT EXISTS `wish_pet_operation` (
    `id`              BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `operation_id`    VARCHAR(64) NOT NULL COMMENT '业务操作唯一键(调用方生成,格式见 api-contract.md)',
    `user_id`         BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `operation_type`  ENUM('EARN','SPEND') NOT NULL COMMENT '操作类型:发放/扣减',
    `amount`          INT NOT NULL COMMENT '请求数量(正整数,实际入账可能因封顶截断)',
    `credited_amount` INT NOT NULL DEFAULT 0 COMMENT '实际入账量(EARN封顶截断后;SPEND等于amount)',
    `balance_after`   INT NOT NULL COMMENT '操作后余额快照',
    `source`          VARCHAR(30) NOT NULL COMMENT '流水来源(ResourceLogSource)',
    `ref_id`          BIGINT UNSIGNED DEFAULT NULL COMMENT '关联业务ID(活动/对战/背包记录,审计用)',
    `request_digest`  CHAR(64) DEFAULT NULL COMMENT '请求摘要SHA-256(用户+类型+金额+来源+refId,冲突判定依据)',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '完成时间(UTC,即原事务提交点)',
    `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_wish_pet_operation` (`id`),
    UNIQUE KEY `uk_wish_pet_operation` (`operation_id`),
    INDEX `idx_wish_pet_operation_user` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物星光交易幂等记录(仅成功结果,重复请求返回原结果)';
