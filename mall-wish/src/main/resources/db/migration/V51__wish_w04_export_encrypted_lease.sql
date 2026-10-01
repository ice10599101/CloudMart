-- W04：导出任务可恢复、私密内容不落明文中转表。
-- 1) content_enc：AES-GCM（ContentCipher v2 信封，AAD 绑定 taskId+userId）加密后的导出内容；
--    明文 content 列停止写入并清空存量——DB 备份中不再出现 DIARY 明文副本；
-- 2) content_sha256：密文完整性校验；
-- 3) lease_owner/lease_until：任务租约——多实例恢复扫描只接管租约过期的任务，
--    单任务单执行者（QA31：两实例不重复处理）。
ALTER TABLE `wish_data_export`
    ADD COLUMN `content_enc` MEDIUMTEXT DEFAULT NULL
        COMMENT '加密导出内容(W04:AES-GCM,AAD=EXPORT:{taskId};明文不落库)' AFTER `download_url`,
    ADD COLUMN `content_sha256` CHAR(64) DEFAULT NULL
        COMMENT '密文SHA-256(下载时完整性校验)' AFTER `content_enc`,
    ADD COLUMN `lease_owner` VARCHAR(64) DEFAULT NULL
        COMMENT '租约持有者(实例+线程;接管时轮换)' AFTER `content_sha256`,
    ADD COLUMN `lease_until` DATETIME(3) DEFAULT NULL
        COMMENT '租约到期(过期可被其他实例接管)' AFTER `lease_owner`;

-- 存量明文一次性清空（合规要求：不留明文副本；任务置 FAILED 让用户重新导出）
UPDATE `wish_data_export`
   SET `content` = NULL,
       `status` = 'FAILED'
 WHERE `content` IS NOT NULL;

ALTER TABLE `wish_data_export`
    ADD INDEX `idx_export_recovery` (`status`, `lease_until`);
