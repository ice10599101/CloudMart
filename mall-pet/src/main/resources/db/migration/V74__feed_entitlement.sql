-- =============================================
-- V74: 实物商品联动（§6 实物商品联动）：兑换码核销 → 双倍喂食权益
--   - pet_feed_entitlement：核销记录（coupon 侧 exchange 幂等键回填 user_coupon_id）
--   约束在服务层：一码一次（uk redemption_code）；核销后发放 1 次"双倍喂食"
--   权益（下一次 feedItem 经验/饱食效果 x2，consume 后标记 used）。
--   商城侧：管理员把实物零食 SKU 与 pet 食物 itemCode 绑定（product 表扩展字段由
--   Nacos 运营配置维护，避免跨服务迁移——见 PetProperties.LinkedProduct）。
-- =============================================
CREATE TABLE IF NOT EXISTS pet_feed_entitlement (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '用户 ID',
    redemption_code VARCHAR(64) NOT NULL COMMENT '兑换码（来自 mall-coupon exchange，一码一次）',
    user_coupon_id BIGINT UNSIGNED NULL COMMENT 'coupon 侧发放的用户券 ID（审计回链）',
    item_code VARCHAR(64) NOT NULL COMMENT '关联宠物食物 itemCode',
    used TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已消耗（下一次 feedItem 双倍后置 1）',
    used_at DATETIME NULL COMMENT '消耗时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_redemption_code (redemption_code),
    INDEX idx_user_used (user_id, used)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '实物商品联动·双倍喂食权益（§6）';
