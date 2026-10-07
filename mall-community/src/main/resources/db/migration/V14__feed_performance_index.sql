-- =============================================
-- V10: 关注流/主页/推荐流复合索引（P2-26 性能演进）
-- 背景：getFollowingFeed / getUserPosts / searchPosts 等读链路的
--       WHERE user_id IN (...) AND status=1 AND review_status=1
--       ORDER BY created_at DESC，原 idx_user_id 单列索引在大关注数/
--       大帖子量下回表+fileSort 放大；复合索引使过滤+排序全索引内完成。
-- 幂等：先删同名索引再建（可重复执行）。
-- =============================================

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'posts'
      AND INDEX_NAME = 'idx_user_status_review_created');

SET @ddl := IF(@idx_exists > 0,
    'SELECT ''idx_user_status_review_created 已存在，跳过'' AS note',
    'CREATE INDEX idx_user_status_review_created ON posts (user_id, status, review_status, created_at)');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
