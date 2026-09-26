-- V42: 通用写操作持久幂等表（B04）+ 虚拟资产 DB 库存模型（B05）
-- 背景 B04：旧幂等依赖 Redis（TTL/易失/跨端点结果串用），Redis 清空或事务提交后
--      缓存写入失败会重复扣费。持久业务凭证必须与领域写操作同事务落库。
-- 背景 B05：库存仅 Redis DECR 预扣，无 DB 扣减；兑换把 spendStarlight 返回的
--      "扣款后余额"误判为"是否扣够"，余额 100 买 80 会被误拒。
-- 兼容：wish_pet_operation 保留既有路径与旧操作键，不得清表后重放旧请求。

CREATE TABLE IF NOT EXISTS `wish_operation` (
    `id`             BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `actor_type`     VARCHAR(16) NOT NULL COMMENT '操作者类型:USER/ADMIN/JOB/SERVICE',
    `actor_id`       BIGINT UNSIGNED NOT NULL COMMENT '操作者ID(数值型主体;服务主体填0)',
    `actor_ref`      VARCHAR(64) DEFAULT NULL COMMENT '非数值主体标识(serviceId,如 mall-job)',
    `operation_type` VARCHAR(48) NOT NULL COMMENT '操作类型:ASSET_EXCHANGE/GIFT_SEND/STARLIGHT_DECAY等',
    `request_key`    VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '请求键(客户端幂等键或业务唯一键)',
    `request_hash`   CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '请求摘要SHA-256(操作类型+目标+业务参数,同键异内容冲突判定)',
    `target_type`    VARCHAR(40) DEFAULT NULL COMMENT '目标类型(资产/心愿/礼物)',
    `target_id`      BIGINT UNSIGNED DEFAULT NULL COMMENT '目标ID',
    `status`         ENUM('PROCESSING','COMPLETED') NOT NULL COMMENT '状态(仅 COMPLETED 对外可见:失败整体回滚)',
    `response_json`  JSON DEFAULT NULL COMMENT '已完成结果(重放依据,不落敏感正文)',
    `error_code`     VARCHAR(80) DEFAULT NULL COMMENT '终态错误码(预留;当前失败即回滚不留凭证)',
    `created_at`     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
    `completed_at`   DATETIME(3) DEFAULT NULL COMMENT '完成时间(UTC)',
    PRIMARY KEY `pk_wish_operation` (`id`),
    UNIQUE KEY `uk_wish_operation` (`actor_type`, `actor_id`, `operation_type`, `request_key`),
    INDEX `idx_wish_operation_actor_time` (`actor_type`, `actor_id`, `created_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通用写操作持久幂等记录(同键同摘要重放,同键异摘要409)';

ALTER TABLE `wish_virtual_asset`
    ADD COLUMN `stock_mode` VARCHAR(16) NOT NULL DEFAULT 'UNLIMITED'
        COMMENT '库存模式:UNLIMITED/LIMITED(B05;DB 为事实,Redis 仅展示缓存)' AFTER `stock`,
    ADD COLUMN `stock_remaining` INT DEFAULT NULL
        COMMENT '剩余库存(LIMITED 非负;条件扣减 stock_remaining>0)' AFTER `stock_mode`,
    ADD COLUMN `version` INT NOT NULL DEFAULT 0
        COMMENT '乐观锁版本(B05)' AFTER `stock_remaining`;

-- 历史回填：stock=0 语义为无限（M/V22）；正数预置为 LIMITED 真实剩余。
-- 运营前置（任务书 10.1/B05）：正数须与历史成功兑换记录核对，差异资产先冻结再修正。
UPDATE `wish_virtual_asset`
    SET `stock_mode` = 'LIMITED', `stock_remaining` = `stock`
    WHERE `stock` IS NOT NULL AND `stock` > 0;
