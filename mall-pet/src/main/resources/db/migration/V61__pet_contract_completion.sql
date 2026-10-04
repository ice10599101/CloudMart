-- V61 (R23 尾/R05 尾): 契约补齐——日记版本号、相册说明/可见性/版本、举报创建加固

-- 1) 日记可见性 PATCH 需要 expectedVersion（§7.2：{visibility, expectedVersion}）
ALTER TABLE `pet_diary_entry`
    ADD COLUMN `version` INT NOT NULL DEFAULT 1 COMMENT '乐观版本（可见性 PATCH CAS）' AFTER `visibility`;

-- 2) 相册补齐（§7.2 album PATCH：{caption?, visibility?, expectedVersion}；§8.2 扩展字段清单）
ALTER TABLE `pet_album_asset`
    ADD COLUMN `caption` VARCHAR(200) DEFAULT NULL COMMENT '照片说明（PATCH 可改）' AFTER `bind_status`,
    ADD COLUMN `visibility` VARCHAR(20) NOT NULL DEFAULT 'OWNER_ONLY' COMMENT '可见性：OWNER_ONLY/PUBLIC（访客仅见 PUBLIC+APPROVED+BOUND）' AFTER `caption`,
    ADD COLUMN `version` INT NOT NULL DEFAULT 1 COMMENT '乐观版本（PATCH CAS）' AFTER `visibility`;

-- 3) R05 举报创建加固：补充说明 + 未结案同人同对象唯一（§7.2/§8.2）
--    "只允许一条未结案举报"用生成列 open_key（§16.2 迁移测试警示：不能拿 nullable resolvedAt
--    拼联合唯一键假装已约束）——PENDING 时非空、结案（HANDLED/REJECTED）自动置 NULL 退出唯一约束。
ALTER TABLE `pet_report`
    ADD COLUMN `description` VARCHAR(1000) DEFAULT NULL COMMENT '补充说明（1~1000 字符）' AFTER `reason`,
    ADD COLUMN `report_date` DATE DEFAULT NULL COMMENT '举报业务日（每日配额统计/审计）' AFTER `is_auto`,
    ADD COLUMN `open_key` VARCHAR(80) GENERATED ALWAYS AS (
        CASE WHEN `status` = 'PENDING'
             THEN CONCAT_WS(':', `reporter_user_id`, `target_type`, `target_id`)
             ELSE NULL END
    ) STORED COMMENT '未结案幂等键：同人同对象至多一张待审，结案自动置 NULL';

ALTER TABLE `pet_report`
    ADD UNIQUE KEY `uk_pet_report_open` (`open_key`);
