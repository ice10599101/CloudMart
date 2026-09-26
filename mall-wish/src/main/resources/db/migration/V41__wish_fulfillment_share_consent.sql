-- V41: 还愿故事分享社区显式授权（B03）
-- 背景：旧行为在提交还愿后无条件把还愿故事 + 成长记录（含解密 DIARY）拼成图文模板
--      发往社区，私密/树洞心愿的日记内容会随帖子外泄。
-- 方案：还愿记录增加分享授权字段；默认关闭自动传播，仅 PUBLIC 心愿且作者显式
--      勾选（share_to_community=1）才允许流转；投递执行时再次校验授权与隐私。

ALTER TABLE `wish_fulfillment`
    ADD COLUMN `share_to_community` TINYINT NOT NULL DEFAULT 0
        COMMENT '是否显式授权分享到社区(B03:默认0,不自动传播)' AFTER `is_inherited`,
    ADD COLUMN `share_consent_at` DATETIME DEFAULT NULL
        COMMENT '分享授权时间(作者勾选时刻)' AFTER `share_to_community`,
    ADD COLUMN `content_version` INT NOT NULL DEFAULT 1
        COMMENT '分享内容版本(去重与撤回判定依据)' AFTER `share_consent_at`,
    ADD COLUMN `share_revoked_at` DATETIME DEFAULT NULL
        COMMENT '分享撤销时间(故事撤回/转私密时回填)' AFTER `content_version`;
