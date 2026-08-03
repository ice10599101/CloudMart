package com.cloudmart.community.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
