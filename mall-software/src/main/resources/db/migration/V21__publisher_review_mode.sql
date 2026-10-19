-- 保留既有授权：已开通用户仍需审核；缺失记录或 can_publish=0 默认禁止。
ALTER TABLE software_publisher_permission
    ADD COLUMN review_required tinyint(1) NOT NULL DEFAULT 1
    COMMENT '1=发布需审核，0=可直接发布（仅 can_publish=1 时有效）' AFTER can_publish;
