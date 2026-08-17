package com.cloudmart.wish.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Flyway 迁移策略配置。
 *
 * <p>启动时先执行 {@code repair()} 清理失败的迁移记录（非致命错误，warn 即可），
 * 再执行 {@code migrate()}。避免因历史失败记录导致服务无法启动。</p>
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
