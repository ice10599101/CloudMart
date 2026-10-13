-- =============================================
-- V79: R02 物品喂食操作幂等台账（修复 AT04：同喂食键双端并发/切宠重复结算）
--   背景：V75 的 pet_feed_entitlement.uk_feed_operation 只能保证"一份权益不被占用两次"，
--   无法约束整个喂食操作——重放分支返回原权益后 feedItem 仍会再扣背包、再应用数值，
--   无权益的普通喂食则完全没有操作键锚点。
--   pet_feed_operation 在喂食事务内登记"已结算操作"：
--   - uk(user_id, operation_id)：同一用户同一喂食键全库只结算一次（含切宠后的重放）
--   - item_code：同键不同食物按 PET_IDEMPOTENCY_CONFLICT 拒绝（禁止键复用）
--   - pet_id/entitlement_id/doubled：结算归属审计（权益不串宠的数据库证据）
--   仅登记成功结算；业务拒绝（已饱/无货）不登记，状态变化后同键允许重试。
-- =============================================
CREATE TABLE IF NOT EXISTS pet_feed_operation (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id        BIGINT UNSIGNED NOT NULL COMMENT '喂食用户 ID',
    operation_id   VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '喂食操作键（客户端 requestKey，服务端兜底 feed-UUID）',
    pet_id         BIGINT UNSIGNED NOT NULL COMMENT '结算时的主宠 ID（重放锚定，不随切宠改投）',
    item_code      VARCHAR(32) NOT NULL COMMENT '食物编码（同键异餐冲突校验）',
    entitlement_id BIGINT UNSIGNED NULL COMMENT '本次占用的双倍权益 ID（普通喂食为 NULL）',
    doubled        TINYINT(1) NOT NULL DEFAULT 0 COMMENT '本次是否触发双倍效果',
    created_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '结算时间(UTC)',
    PRIMARY KEY pk_pet_feed_operation (id),
    UNIQUE KEY uk_feed_op_user_operation (user_id, operation_id),
    INDEX idx_feed_op_pet (pet_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '物品喂食已结算操作台账（R02 幂等）';
