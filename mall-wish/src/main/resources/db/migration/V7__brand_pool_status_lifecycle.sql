-- T11 品牌许愿池生命周期补齐：
-- 代码已引入 DRAFT（草稿）/ SETTLING（结算中）/ CONFIG_ERROR（结算失败）等状态，
-- 而 V1 基线的 `status` 枚举只有 ('ACTIVE','ENDED')，导致创建草稿池时
-- "Data truncated for column 'status'"（写 DRAFT 被截断 → 500）。
-- 此处把枚举扩展为完整生命周期，不改已存在行的值。

ALTER TABLE `wish_brand_pool`
    MODIFY COLUMN `status` enum('DRAFT','ACTIVE','SETTLING','CONFIG_ERROR','ENDED','COMPLETED')
    NOT NULL DEFAULT 'DRAFT' COMMENT '池状态: DRAFT/ACTIVE/SETTLING/CONFIG_ERROR/ENDED/COMPLETED';
