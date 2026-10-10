-- =============================================
-- V7: 订阅消息绑定（N-1）：用户在小程序授权一次 = 一条发送额度
--   - openid 由 code2session 换取（Taro.login code），每行即一次授权额度
--   - consumed：发送后标记；43101（用户拒收/额度耗尽）亦标记
-- =============================================
CREATE TABLE IF NOT EXISTS subscribe_message_binding (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '用户 ID',
    openid VARCHAR(64) NOT NULL COMMENT '用户小程序 openid（code2session）',
    template_key VARCHAR(32) NOT NULL COMMENT '业务模板键（ANNIVERSARY 等）',
    consumed TINYINT(1) NOT NULL DEFAULT 0 COMMENT '额度是否已消耗',
    consumed_at DATETIME NULL COMMENT '消耗时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间',
    PRIMARY KEY (id),
    INDEX idx_user_key_consumed (user_id, template_key, consumed)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '订阅消息授权额度（N-1）';
