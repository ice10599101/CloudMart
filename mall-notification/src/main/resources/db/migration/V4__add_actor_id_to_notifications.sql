-- 通知增加操作者字段：让「谁点赞/收藏/关注了我」可点击跳转到其个人主页
-- NULL = 系统/广播/历史通知（无法关联操作者）
ALTER TABLE notifications
    ADD COLUMN actor_id BIGINT UNSIGNED NULL COMMENT '操作者用户ID（谁做的互动），NULL=系统/历史通知' AFTER biz_type;
