-- PRODUCT-01：商品问答改两表模型（question 一问多答 answer）
-- 1) 新答案表；2) 旧单列答案数据搬运（每人一条，历史单答案→一条 answer 行）；
-- 3) product_question 删除 answer 相关列（模型收敛，防双写混淆）。
CREATE TABLE `product_answer` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `question_id` bigint unsigned NOT NULL COMMENT '问题ID',
  `product_id` bigint unsigned NOT NULL COMMENT '商品ID(冗余, 归属校验与查询)',
  `author_user_id` bigint unsigned NOT NULL COMMENT '回答人用户ID',
  `body` varchar(500) NOT NULL COMMENT '回答内容(HTML转义, 1..500字)',
  `purchased` tinyint(1) NOT NULL DEFAULT '0' COMMENT '回答人已购快照(回答时点)',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0 正常 / 1 隐藏',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_answer_author` (`question_id`, `author_user_id`),
  KEY `idx_answer_product_status` (`product_id`, `status`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品问答答案表(PRODUCT-01 一问多答)';

-- 历史单列答案搬运（已回答问题 → answer 行；作者为原 answer_user_id）
INSERT INTO `product_answer` (`question_id`, `product_id`, `author_user_id`, `body`, `purchased`, `status`, `created_at`)
SELECT `id`, `product_id`, `answer_user_id`, `answer`,
       COALESCE(`answer_purchased`, 0), 0,
       COALESCE(`answered_at`, `updated_at`, `created_at`)
FROM `product_question`
WHERE `answer` IS NOT NULL AND `answer_user_id` IS NOT NULL;

ALTER TABLE `product_question`
    DROP COLUMN `answer`,
    DROP COLUMN `answer_user_id`,
    DROP COLUMN `answer_purchased`,
    DROP COLUMN `answered_at`,
    MODIFY COLUMN `question` varchar(500) NOT NULL COMMENT '问题内容(HTML转义, 1..500字)',
    ADD KEY `idx_question_product_status` (`product_id`, `status`, `id`);
