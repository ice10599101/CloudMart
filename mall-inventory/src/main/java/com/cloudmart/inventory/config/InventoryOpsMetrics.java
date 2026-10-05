package com.cloudmart.inventory.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * T27 运维指标：库存台账/预占一致性、预占滞留与 Outbox 死信的可观测性
 * （对齐 E03 支付域模式：Gauge 拉取式聚合，scrape 时执行一次聚合 SQL）。
 *
 * <p>告警建议（Grafana/Prometheus 规则）：
 * <ul>
 *   <li>inventory_reserved_ledger_diff != 0 → 台账与可售投影漂移，需人工对账
 *       （方案 T27 零容忍："库存不超卖"）；</li>
 *   <li>inventory_reservation_stuck_oldest_seconds > 超时释放周期×2 →
 *       超时释放任务卡住（预占滞留挤占可售）；</li>
 *   <li>inventory_outbox_dead_total 增长 → 补偿/确认事件投递耗尽需人工介入。</li>
 * </ul></p>
 */
@Component
public class InventoryOpsMetrics {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public InventoryOpsMetrics(MeterRegistry registry, JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;

        Gauge.builder("inventory_reserved_ledger_diff", this, m -> count(
                "SELECT COALESCE(SUM(t.ledger_delta), 0) FROM ("
                        + "SELECT i.reserved - COALESCE(r.reserved_sum, 0) AS ledger_delta "
                        + "FROM inventory i LEFT JOIN ("
                        + "SELECT sku_id, SUM(quantity) AS reserved_sum FROM inventory_reservation "
                        + "WHERE status = 'RESERVED' GROUP BY sku_id) r ON r.sku_id = i.sku_id) t"))
                .description("预占投影与台账差值总量（Σ inventory.reserved − Σ RESERVED 台账；非 0=漂移需对账）")
                .register(registry);

        Gauge.builder("inventory_reservation_stuck_oldest_seconds", this, m -> seconds(
                "SELECT COALESCE(TIMESTAMPDIFF(SECOND, MIN(created_at), NOW(3)), 0) FROM inventory_reservation "
                        + "WHERE status = 'RESERVED'"))
                .description("RESERVED 预占最老滞留时长（秒；超过超时释放周期×2 告警）")
                .register(registry);

        Gauge.builder("inventory_outbox_dead_total", this, m -> count(
                "SELECT COUNT(*) FROM outbox_event WHERE status = 'DEAD'"))
                .description("库存域 Outbox 死信数（确认/释放事件投递耗尽，需人工介入）")
                .register(registry);

        Gauge.builder("inventory_outbox_pending_oldest_seconds", this, m -> seconds(
                "SELECT COALESCE(TIMESTAMPDIFF(SECOND, MIN(created_at), NOW(3)), 0) FROM outbox_event "
                        + "WHERE status = 'PENDING'"))
                .description("库存域 Outbox 最老待发事件年龄（秒；>300 告警）")
                .register(registry);
    }

    private double count(String sql) {
        try {
            Long result = jdbcTemplate.queryForObject(sql, Long.class);
            return result == null ? 0 : result;
        } catch (Exception e) {
            // 表不存在/库不可达：指标报 0 不阻断 scrape（Prometheus 端可从无数据判断异常）
            return 0;
        }
    }

    private double seconds(String sql) {
        try {
            Long result = jdbcTemplate.queryForObject(sql, Long.class);
            return result == null ? 0 : result;
        } catch (Exception e) {
            return 0;
        }
    }
}
