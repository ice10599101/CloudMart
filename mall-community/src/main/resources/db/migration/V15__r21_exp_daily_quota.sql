-- =============================================
-- V15: R21 社区经验日配额原子占用表
-- 替代旧实现"内存求和判 200 上限"（并发可超发）：
-- 发放在事务内按 (user_id, business_date) 行锁（SELECT ... FOR UPDATE）串行占用，
-- granted 原子累加，actual = min(requested, 200 - granted)，100 并发也不突破日上限。
-- 幂等：uk_user_date 保证每用户每天一行；业务日期口径 R21 暂取服务器日（与旧实现一致），
--       R22 统一业务日期时切换为用户业务日。
-- =============================================
CREATE TABLE IF NOT EXISTS `community_exp_daily_quota` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`       BIGINT UNSIGNED NOT NULL COMMENT '用户 ID',
    `business_date` DATE NOT NULL COMMENT '业务日期（服务器日；R22 起为用户业务日）',
    `granted`       INT NOT NULL DEFAULT 0 COMMENT '当日已占用经验额度（原子累加，<=200）',
    `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_date` (`user_id`, `business_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='R21 社区经验日配额占用（行锁原子发放）';
