ALTER TABLE users ADD COLUMN nickname_updated_at DATETIME DEFAULT NULL COMMENT '昵称上次修改时间' AFTER nickname;
