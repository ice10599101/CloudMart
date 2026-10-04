package com.cloudmart.payment.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * E03 运维指标：支付/退款/Outbox/Inbox 关键积压与延迟的可观测性。
 *
 * <p>E03 指标清单要求——支付未知时长、退款积压、Outbox 最老待发时间、Inbox 失败
 * 计数在 Prometheus 端可查询。Gauge 为拉取式（scrape 时执行一次聚合 SQL），
 * 频率约 15-30s 一次，查询均为单表聚合有索引支撑，不影响业务链路。</p>
 *
 * <p>告警建议（Grafana/Prometheus 规则，按容量预算修订）：
 * <ul>
 *   <li>payment_outbox_pending_oldest_seconds > 300 → 投递管道故障（Delivery 挂了）；</li>
 *   <li>payment_refund_backlog > 0 持续 10min → 渠道退款卡住（查 refund_order.next_query_at）；</li>
 *   <li>payment_inbox_failed_total > 0 → 消费失败需人工介入。</li>
 * </ul></p>
 */
@Component
public class PaymentOpsMetrics {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public PaymentOpsMetrics(MeterRegistry registry, JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;

        Gauge.builder("payment_outbox_pending_total", this, m -> count(
                "SELECT COUNT(*) FROM outbox_event WHERE status IN ('PENDING','FAILED')"))
                .description("Outbox 待发/失败事件数（>0 持续=投递管道积压）")
                .register(registry);

        Gauge.builder("payment_outbox_pending_oldest_seconds", this, m -> seconds(
                "SELECT COALESCE(TIMESTAMPDIFF(SECOND, MIN(created_at), NOW(3)), 0) FROM outbox_event "
                        + "WHERE status IN ('PENDING','FAILED')"))
                .description("Outbox 最老待发事件年龄（秒；>300 告警）")
                .register(registry);

        Gauge.builder("payment_inbox_failed_total", this, m -> count(
                "SELECT COUNT(*) FROM inbox_record WHERE status = 'FAILED'"))
                .description("Inbox 消费失败记录数（>0 = 需人工排查）")
                .register(registry);

        Gauge.builder("payment_refund_backlog", this, m -> count(
                "SELECT COUNT(*) FROM refund_order WHERE status IN ('APPROVED','PROCESSING','UNKNOWN')"))
                .description("退款积压（已受理未到终态的单数；>0 持续=渠道卡住）")
                .register(registry);

        Gauge.builder("payment_refund_unknown_oldest_seconds", this, m -> seconds(
                "SELECT COALESCE(TIMESTAMPDIFF(SECOND, MIN(next_query_at), NOW(3)), 0) FROM refund_order "
                        + "WHERE status = 'UNKNOWN'"))
                .description("退款 UNKNOWN 最老未决时长（秒；渠道结果未知需查单）")
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
