-- V52 (T16)：wish outbox 租约栅栏（fencing）——认领递增 lease_version，
-- markPublished/markRetryOrDead 绑定 owner+leaseVersion，失去租约的旧实例
-- 迟到回写被拒（T16 证据：原实现仅按 eventId+status 条件回写，可覆盖新执行者）。

ALTER TABLE wish_outbox
    ADD COLUMN `lease_version` INT NOT NULL DEFAULT 0
        COMMENT '租约版本（T16 fencing：每次认领+1，回写校验）' AFTER lease_until;
