-- 公告全站推送复用 notifications 表：管理端公告为富文本 HTML，varchar(1000) 容量不足。
-- varchar(1000) → TEXT 为非破坏性扩容，不改动既有数据与索引。
ALTER TABLE `notifications`
    MODIFY COLUMN `content` text NOT NULL COMMENT '通知内容（公告推送为富文本 HTML，扩容为 TEXT）';
