package com.cloudmart.pet.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Flyway 迁移策略（R01：发布保护）。
 *
 * <p>原实现每次启动无条件 {@code repair()} 并关闭 validate——repair 会把历史迁移的
 * checksum 漂移"对齐"掉，弱化篡改检测，也让 V28/V29 等破坏性迁移在任何环境都能静默通过。
 * 现行为：</p>
 *
 * <ul>
 *   <li>不再自动 repair：checksum 漂移/失败历史行必须走受控修复单（validate-on-migrate 已开启，
 *       漂移直接阻断启动）。</li>
 *   <li>migrate 前执行只读 preflight：待执行迁移包含 V28（删旧物种宠物）/V29（TRUNCATE 运行数据）
 *       且运行表已有真实数据时终止发布——禁止靠"应该都是测试数据"判断放行；
 *       空库（表不存在按空处理）与已执行过 V28/V29 的库不受影响。</li>
 * </ul>
 *
 * <p>有数据的旧版本升级必须走《宠物模块改造实施方案》第 10.2 节的 legacy-upgrade 路线，
 * 在克隆环境演练后由独立迁移 Job 执行，不由应用启动时自动跑。</p>
 */
@Configuration
@Slf4j
public class FlywayRepairConfig {

    /** 破坏性迁移版本 → 说明（待执行且库里有数据时阻断启动） */
    private static final Set<String> DESTRUCTIVE_PENDING_VERSIONS = Set.of("28", "29");

    /** preflight 抽查的运行数据表（V29 清零对象中的核心经济/资产表；表不存在按空处理） */
    private static final List<String> RUNTIME_TABLES =
            List.of("pet", "pet_wallet_account", "pet_wallet_ledger", "pet_inventory", "pet_purchase_order");

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy(DataSource dataSource) {
        return flyway -> {
            requireSafeMigrationPath(flyway, dataSource);
            log.info("Starting Flyway migration (validate-on-migrate=true, auto-repair disabled)...");
            flyway.migrate();
            log.info("Flyway migration completed");
        };
    }

    /** 只读 preflight：破坏性迁移待执行 + 运行表非空 → 快速失败，不执行任何迁移 */
    private void requireSafeMigrationPath(org.flywaydb.core.Flyway flyway, DataSource dataSource) {
        Set<String> pendingVersions = Arrays.stream(flyway.info().pending())
                .map(info -> info.getVersion().getVersion())
                .collect(Collectors.toSet());
        Set<String> destructivePending = pendingVersions.stream()
                .filter(DESTRUCTIVE_PENDING_VERSIONS::contains)
                .collect(Collectors.toSet());
        if (destructivePending.isEmpty()) {
            return;
        }
        long runtimeRows = countRuntimeRows(dataSource);
        if (runtimeRows > 0) {
            throw new IllegalStateException(
                    "检测到破坏性迁移 V" + String.join("/V", destructivePending) + " 待执行，且宠物运行表已有 "
                            + runtimeRows + " 行真实数据——已按 R01 发布保护阻断启动。"
                            + "有数据的旧版本升级必须走方案第 10.2 节 legacy-upgrade 路线（克隆演练 + 独立迁移 Job），"
                            + "禁止由应用启动自动执行。");
        }
        log.warn("破坏性迁移 V{} 待执行，运行表为空（开发空库），放行迁移", destructivePending);
    }

    /** 运行表行数统计（表不存在 → 视为空库；计数失败视为不安全，阻断而非放行） */
    private long countRuntimeRows(DataSource dataSource) {
        var jdbcTemplate = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        long total = 0;
        for (String table : RUNTIME_TABLES) {
            try {
                Long count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM `" + table + "`", Long.class);
                total += count != null ? count : 0;
            } catch (org.springframework.dao.DataAccessException e) {
                String message = String.valueOf(e.getMessage());
                if (message.contains("doesn't exist") || message.contains("does not exist")) {
                    log.debug("preflight：表 {} 不存在（新库），按空处理", table);
                } else {
                    throw new IllegalStateException(
                            "R01 preflight 无法确认表 " + table + " 的数据量，按不安全处理（禁止盲目放行破坏性迁移）", e);
                }
            }
        }
        return total;
    }
}
