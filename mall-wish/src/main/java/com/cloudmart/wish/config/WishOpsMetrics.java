package com.cloudmart.wish.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * T27 运维指标：心愿域 Outbox 积压/死信与奖励发放卡住的可观测性
 * （对齐 E03 支付域模式：Gauge 拉取式聚合，scrape 时执行一次单表聚合 SQL，
 * 有索引支撑不影响业务链路）。
 *
 * <p>告警建议（Grafana/Prometheus 规则，按容量预算修订）：
 * <ul>
 *   <li>wish_outbox_pending_oldest_seconds > 300 → 事件投递管道故障；</li>
 *   <li>wish_outbox_dead_total 增长 → 事件重试耗尽需人工介入（T16 重试入口）；</li>
 *   <li>wish_reward_dispatch_stuck > 0 → 奖励（星光/徽章）发放事件卡住——
 *       方案 T27"奖励待到账数"指标：用户侧表现为签到/活动奖励未入账。</li>
 * </ul></p>
 */
@Component
public class WishOpsMetrics {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public WishOpsMetrics(MeterRegistry registry, JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;

        Gauge.builder("wish_outbox_pending_total", this, m -> count(
                "SELECT COUNT(*) FROM wish_outbox WHERE status = 'PENDING'"))
                .description("心愿域 Outbox 待发事件数（>0 持续=投递管道积压）")
                .register(registry);

        Gauge.builder("wish_outbox_pending_oldest_seconds", this, m -> seconds(
                "SELECT COALESCE(TIMESTAMPDIFF(SECOND, MIN(created_at), NOW(3)), 0) FROM wish_outbox "
                        + "WHERE status = 'PENDING'"))
                .description("心愿域 Outbox 最老待发事件年龄（秒；>300 告警）")
                .register(registry);

        Gauge.builder("wish_outbox_dead_total", this, m -> count(
                "SELECT COUNT(*) FROM wish_outbox WHERE status = 'DEAD'"))
                .description("心愿域 Outbox 死信数（增长=重试耗尽，T16 后台可受控重试）")
                .register(registry);

        Gauge.builder("wish_reward_dispatch_stuck", this, m -> count(
                "SELECT COUNT(*) FROM wish_outbox WHERE status = 'DEAD' AND aggregate_type = 'WALLET'"))
                .description("奖励发放卡住数（方案 T27'奖励待到账数'：WALLET 聚合死信=星光/徽章未入账）")
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
