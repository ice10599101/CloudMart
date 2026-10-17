-- WISH-02：离线打卡事实完整性——打卡原发生时刻（UTC）
-- 离线补传时 occurred_at_utc = 客户端入队时刻（意图日期服务端核验，跨日拒绝不补记）；
-- 在线打卡为 NULL，语义回退 created_at。存量数据不回填（历史行以 created_at 为准）。
ALTER TABLE `wish_checkin`
    ADD COLUMN `occurred_at_utc` datetime NULL COMMENT '打卡原发生时刻(UTC; 离线补传为入队时刻, WISH-02)' AFTER `starlight_granted`;
