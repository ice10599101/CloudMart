-- F8 偏差修正：AI 人设口头禅入库——每种性格一行，管理端编辑；
-- 服务端 60 秒 TTL 缓存定时同步（多实例收敛 ≤60s），DB 无行时回落 Nacos 兜底配置。

CREATE TABLE IF NOT EXISTS `pet_persona_phrase` (
    `id`          BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `personality` VARCHAR(16) NOT NULL COMMENT '性格编码(PetPersonality 枚举)',
    `phrase`      VARCHAR(64) NOT NULL COMMENT '口头禅模板({name} 占位宠物名)',
    `updated_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    PRIMARY KEY `pk_pet_persona_phrase` (`id`),
    UNIQUE KEY `uk_persona_personality` (`personality`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物AI人设口头禅(按性格)';
