-- V61 (T22)：时间胶囊改期——允许规则下改期（方案 T22 玩法行），改期次数有界。

ALTER TABLE `time_capsule`
    ADD COLUMN `reschedule_count` INT NOT NULL DEFAULT 0
        COMMENT '已改期次数（T22：上限 3 次，超出拒绝）' AFTER open_at_timezone,
    ADD COLUMN `reschedule_limit` INT NOT NULL DEFAULT 3
        COMMENT '改期次数上限（T22：服务端权威，客户端不可指定）' AFTER reschedule_count;
