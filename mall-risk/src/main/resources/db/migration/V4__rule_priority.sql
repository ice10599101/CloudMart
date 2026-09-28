-- RISK-01：规则稳定优先级——小者先评（原遍历命中首个，顺序依赖 DB 返回不稳定，
-- 同规则集可能产生不同结论）；存量规则按 id 顺序回填优先级
ALTER TABLE risk_rules
    ADD COLUMN priority INT NOT NULL DEFAULT 0 COMMENT '评估优先级（小者先评）' AFTER status;

UPDATE risk_rules SET priority = id WHERE priority = 0 OR priority IS NULL;
