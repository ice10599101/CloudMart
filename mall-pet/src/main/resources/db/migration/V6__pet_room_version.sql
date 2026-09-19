-- V6: 补齐 pet_room.version（V4 建表漏列，PetRoom 实体带 @Version 乐观锁）
-- 影响：GET /pet/home（PetHomeServiceImpl.ensureRoom）查询含 version，缺列时报
--      "Unknown column 'version' in 'field list'" → 500，前端误判为"无宠物"反复弹领养框。
-- 说明：MySQL 不支持 ADD COLUMN IF NOT EXISTS，故用 information_schema 预检后动态执行，
--      脚本可重复执行（幂等），不会因列已存在而让 Flyway 失败。

SET @schema_name = DATABASE();
SET @table_name = 'pet_room';
SET @column_name = 'version';
SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = @table_name AND COLUMN_NAME = @column_name
);
SET @ddl = IF(@column_exists = 0,
    'ALTER TABLE `pet_room` ADD COLUMN `version` INT NOT NULL DEFAULT 0 COMMENT ''乐观锁版本号(摆放/主题并发写保护)''',
    'SELECT ''pet_room.version already exists, skip''');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
