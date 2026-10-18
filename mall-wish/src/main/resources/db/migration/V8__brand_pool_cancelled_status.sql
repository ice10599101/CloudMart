-- T11 品牌池手动结束（ACTIVE→CANCELLED）使用的状态在 V7 生命周期枚举中缺失，
-- 导致 end 接口写 CANCELLED 时 "Data truncated for column 'status'"。
-- 补齐 CANCELLED（V7 已上线且其 checksum 不可改，故用新版本扩展）。

ALTER TABLE `wish_brand_pool`
    MODIFY COLUMN `status` enum('DRAFT','ACTIVE','SETTLING','CONFIG_ERROR','CANCELLED','ENDED','COMPLETED')
    NOT NULL DEFAULT 'DRAFT' COMMENT '池状态: DRAFT/ACTIVE/SETTLING/CONFIG_ERROR/CANCELLED/ENDED/COMPLETED';
