-- FILE-01：文件资产台账——归属、内容指纹、可见性与引用计数
-- 所有上传（新旧接口）均登记 file_asset；删除按 fileId 校验归属并检查引用；
-- 存量磁盘文件不回填（无归属信息），按 LEGACY_UNCLAIMED 由管理员审核。

CREATE TABLE file_asset
(
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '资产ID（对外 string）',
    owner_id       BIGINT UNSIGNED NULL COMMENT '归属用户ID；NULL=平台/存量无主（LEGACY_UNCLAIMED 仅管理员可处置）',
    original_name  VARCHAR(255)    NOT NULL COMMENT '原始文件名（展示用，已去路径）',
    storage_key    VARCHAR(512)    NOT NULL COMMENT '存储键（相对存储根路径，唯一）',
    mime           VARCHAR(100)    NOT NULL COMMENT '实际嗅探的 MIME 类型（魔数判定，非客户端声明）',
    size_bytes     BIGINT UNSIGNED NOT NULL COMMENT '文件大小（字节）',
    sha256         CHAR(64)        NOT NULL COMMENT '内容 SHA-256（去重与完整性）',
    visibility     VARCHAR(20)     NOT NULL DEFAULT 'PUBLIC' COMMENT '可见性：PUBLIC/PRIVATE（私聊/售后凭证等）',
    status         VARCHAR(30)     NOT NULL DEFAULT 'READY' COMMENT '状态：READY/DELETED/LEGACY_UNCLAIMED',
    created_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY pk_file_asset (id),
    UNIQUE KEY uk_file_asset_storage_key (storage_key),
    KEY idx_file_asset_owner (owner_id, created_at),
    KEY idx_file_asset_sha256 (sha256)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT '文件资产台账';

-- 业务引用：消费方（帖子/头像/售后凭证等）登记对 fileId 的引用；
-- 删除被引用文件返回 409，引用清零后才可物理删除。
CREATE TABLE file_reference
(
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '引用ID',
    asset_id     BIGINT UNSIGNED NOT NULL COMMENT '被引用的 file_asset.id',
    biz_type     VARCHAR(50)     NOT NULL COMMENT '业务类型（如 POST/AVATAR/AFTER_SALE）',
    biz_id       VARCHAR(64)     NOT NULL COMMENT '业务主键',
    created_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY pk_file_reference (id),
    UNIQUE KEY uk_file_reference_biz (asset_id, biz_type, biz_id),
    KEY idx_file_reference_asset (asset_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT '文件业务引用';
