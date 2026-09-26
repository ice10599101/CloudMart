package com.cloudmart.pet.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Flyway 迁移策略（与 mall-wish FlywayRepairConfig 同构）。
 *
 * <p>启动时先 {@code repair()} 清理失败迁移历史行（MySQL DDL 非事务性，失败迁移
 * 会留下 success=0 记录并阻断后续启动），再 {@code migrate()}——保证新增迁移
 * 修复后重启即可自愈，无需手工清理数据库。</p>
 */
@Configuration
@Slf4j
public class FlywayRepairConfig {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            try {
                log.info("Executing Flyway repair to clean failed migration records...");
                flyway.repair();
                log.info("Flyway repair completed");
            } catch (Exception e) {
                log.warn("Flyway repair failed (non-fatal): {}", e.getMessage());
            }
            log.info("Starting Flyway migration...");
            flyway.migrate();
            log.info("Flyway migration completed");
        };
    }
}
