-- V2: 用户表扩展个人资料字段，移除手机号，username 改为小答号（自增5位数字）

DROP PROCEDURE IF EXISTS add_column_if_not_exists;
DELIMITER //
CREATE PROCEDURE add_column_if_not_exists(
    IN table_name_param VARCHAR(100),
    IN column_name_param VARCHAR(100),
    IN column_definition VARCHAR(500)
)
BEGIN
    DECLARE column_exists INT DEFAULT 0;
    SELECT COUNT(*) INTO column_exists
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = table_name_param
      AND COLUMN_NAME = column_name_param;

    IF column_exists = 0 THEN
        SET @sql = CONCAT('ALTER TABLE `', table_name_param, '` ADD COLUMN `', column_name_param, '` ', column_definition);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

DROP PROCEDURE IF EXISTS drop_index_if_exists;
DELIMITER //
CREATE PROCEDURE drop_index_if_exists(
    IN table_name_param VARCHAR(100),
    IN index_name_param VARCHAR(100)
)
BEGIN
    DECLARE index_exists INT DEFAULT 0;
    SELECT COUNT(*) INTO index_exists
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = table_name_param
      AND INDEX_NAME = index_name_param;

    IF index_exists > 0 THEN
        SET @sql = CONCAT('ALTER TABLE `', table_name_param, '` DROP INDEX `', index_name_param, '`');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

DROP PROCEDURE IF EXISTS drop_column_if_exists;
DELIMITER //
CREATE PROCEDURE drop_column_if_exists(
    IN table_name_param VARCHAR(100),
    IN column_name_param VARCHAR(100)
)
BEGIN
    DECLARE column_exists INT DEFAULT 0;
    SELECT COUNT(*) INTO column_exists
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = table_name_param
      AND COLUMN_NAME = column_name_param;

    IF column_exists > 0 THEN
        SET @sql = CONCAT('ALTER TABLE `', table_name_param, '` DROP COLUMN `', column_name_param, '`');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

DROP PROCEDURE IF EXISTS add_index_if_not_exists;
DELIMITER //
CREATE PROCEDURE add_index_if_not_exists(
    IN table_name_param VARCHAR(100),
    IN index_name_param VARCHAR(100),
    IN index_columns VARCHAR(500)
)
BEGIN
    DECLARE index_exists INT DEFAULT 0;
    SELECT COUNT(*) INTO index_exists
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = table_name_param
      AND INDEX_NAME = index_name_param;

    IF index_exists = 0 THEN
        SET @sql = CONCAT('ALTER TABLE `', table_name_param, '` ADD INDEX `', index_name_param, '` (', index_columns, ')');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CALL add_column_if_not_exists('users', 'signature', "varchar(200) DEFAULT NULL COMMENT '个性签名' AFTER `avatar`");
CALL add_column_if_not_exists('users', 'gender', "varchar(10) DEFAULT NULL COMMENT '性别' AFTER `signature`");
CALL add_column_if_not_exists('users', 'birthday', "varchar(20) DEFAULT NULL COMMENT '生日' AFTER `gender`");
CALL add_column_if_not_exists('users', 'constellation', "varchar(20) DEFAULT NULL COMMENT '星座' AFTER `birthday`");
CALL add_column_if_not_exists('users', 'occupation', "varchar(50) DEFAULT NULL COMMENT '职业' AFTER `constellation`");
CALL add_column_if_not_exists('users', 'school', "varchar(100) DEFAULT NULL COMMENT '学校' AFTER `occupation`");
CALL add_column_if_not_exists('users', 'location', "varchar(100) DEFAULT NULL COMMENT '所在地区' AFTER `school`");
CALL add_column_if_not_exists('users', 'hobbies', "varchar(200) DEFAULT NULL COMMENT '兴趣爱好' AFTER `location`");

CALL drop_index_if_exists('users', 'uk_phone');
CALL drop_column_if_exists('users', 'phone');

ALTER TABLE `users` MODIFY COLUMN `username` varchar(50) NOT NULL COMMENT '小答号';

CALL add_index_if_not_exists('users', 'idx_users_created_at', '`created_at`');

DROP PROCEDURE IF EXISTS add_column_if_not_exists;
DROP PROCEDURE IF EXISTS drop_index_if_exists;
DROP PROCEDURE IF EXISTS drop_column_if_exists;
DROP PROCEDURE IF EXISTS add_index_if_not_exists;
