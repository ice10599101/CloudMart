package com.cloudmart.order.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * T27 运维指标：订单域 Outbox 积压与数据库锁等待的可观测性
 * （对齐 E03 模式：Gauge 拉取式聚合，异常报 0 不阻断 scrape）。
 *
 * <p>说明：payment_outbox_* 指标从 mall-payment 连接**支付库**的公共
 * outbox_event 表采集；本类从**订单库**采集同构表——两库独立，
 * 缺一不可（跨库事件各自投递）。</p>
 *
 * <p>告警建议：order_outbox_pending_oldest_seconds > 300（投递管道故障）；
 * order_db_lock_waits > 0 持续（热点行锁竞争，结合慢查询定位）。</p>
 */
@Component
public class OrderOpsMetrics {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public OrderOpsMetrics(MeterRegistry registry, JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;

        Gauge.builder("order_outbox_pending_total", this, m -> count(
                "SELECT COUNT(*) FROM outbox_event WHERE status IN ('PENDING','FAILED')"))
                .description("订单域 Outbox 待发/失败事件数（>0 持续=投递管道积压）")
                .register(registry);

        Gauge.builder("order_outbox_pending_oldest_seconds", this, m -> seconds(
                "SELECT COALESCE(TIMESTAMPDIFF(SECOND, MIN(created_at), NOW(3)), 0) FROM outbox_event "
                        + "WHERE status IN ('PENDING','FAILED')"))
                .description("订单域 Outbox 最老待发事件年龄（秒；>300 告警）")
                .register(registry);

        Gauge.builder("order_outbox_dead_total", this, m -> count(
                "SELECT COUNT(*) FROM outbox_event WHERE status = 'DEAD'"))
                .description("订单域 Outbox 死信数（T16 后台可受控重试）")
                .register(registry);

        Gauge.builder("order_db_lock_waits", this, m -> count(
                "SELECT COUNT(*) FROM information_schema.INNODB_TRX "
                        + "WHERE trx_state = 'LOCK WAIT'"))
                .description("当前锁等待事务数（>0 持续=热点行竞争，结合慢查询定位）")
                .register(registry);
    }

    private double count(String sql) {
        try {
            Long result = jdbcTemplate.queryForObject(sql, Long.class);
            return result == null ? 0 : result;
        } catch (Exception e) {
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
