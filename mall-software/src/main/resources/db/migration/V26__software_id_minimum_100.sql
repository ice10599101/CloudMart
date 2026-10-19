-- 新软件 ID 从至少 100 开始。保留现有 ID 与所有外键/逻辑引用不变；
-- 若当前 AUTO_INCREMENT 已高于 100（例如线上已有较多数据），MySQL 保持更大的下一个值。
ALTER TABLE `software` AUTO_INCREMENT = 100;
