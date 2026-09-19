-- V5: 宠物性别（领养时选择；存量数据默认男，可在后续档案编辑中调整）
-- 约定: 与 species/personality 同为白名单枚举, 服务端权威校验, 越界值 400

ALTER TABLE `pet`
    ADD COLUMN `gender` ENUM('MALE', 'FEMALE') NOT NULL DEFAULT 'MALE'
        COMMENT '性别:MALE男/FEMALE女(领养时选择,存量默认男)' AFTER `species`;
