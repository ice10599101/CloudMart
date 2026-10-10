-- =============================================
-- V3: 商品问答（N-5 问大家）：购前决策辅助 + 喂 AI 语料
-- 状态：0 正常 / 1 隐藏（管理端处置，对齐 posts.moderation_hidden 语义简化版）
-- =============================================
CREATE TABLE IF NOT EXISTS product_question (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    product_id BIGINT UNSIGNED NOT NULL COMMENT '商品 ID',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '提问人用户 ID',
    question VARCHAR(500) NOT NULL COMMENT '问题内容',
    answer VARCHAR(2000) NULL COMMENT '回答内容（可空）',
    answer_user_id BIGINT UNSIGNED NULL COMMENT '回答人用户 ID（任意登录用户可答，前端标注已购身份）',
    answer_purchased TINYINT(1) NOT NULL DEFAULT 0 COMMENT '回答人是否已购该商品（回答时快照）',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0 正常 / 1 隐藏',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    answered_at DATETIME NULL COMMENT '回答时间',
    PRIMARY KEY (id),
    INDEX idx_product_status (product_id, status),
    INDEX idx_user_id (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '商品问答（N-5 问大家）';
