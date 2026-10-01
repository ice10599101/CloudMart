package com.cloudmart.order.config;


import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;

/**
 * Flyway 迁移策略（与 mall-community 一致）：先 repair 清理失败迁移记录
 * （半迁移状态/失败 DDL 残留），再 migrate。开发期迁移文件对存量数据
 * 自愈（如 V6 背填空 request_key），失败记录不阻塞后续启动重试。
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
