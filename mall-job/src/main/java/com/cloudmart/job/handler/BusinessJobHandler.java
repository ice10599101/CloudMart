package com.cloudmart.job.handler;

import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;

import java.util.Map;

/**
 * 业务定时任务 Handler 集合。
 * 通过 XXL-JOB 统一调度，支持集群环境下的分布式任务执行。
 */
@Component
public class BusinessJobHandler {

    private static final Logger log = LoggerFactory.getLogger(BusinessJobHandler.class);

    private final RestClient restClient;

    public BusinessJobHandler(@LoadBalanced RestClient.Builder restClientBuilder) {
        // 服务名 URI（http://mall-wish 等）经 LoadBalancer→Nacos 解析为实际地址
        this.restClient = restClientBuilder.build();
    }

    /**
     * 拼团超时处理：扫描超时未成团的拼团组并触发退款。
     * 由 XXL-JOB 调度中心每 5 分钟触发一次。
     */
    @XxlJob("groupExpirationHandler")
    public void groupExpirationHandler() {
        log.info("XXL-JOB: 开始执行拼团超时处理...");
        try {
            restClient.post()
                    .uri("http://mall-marketing/marketing/group/expiration")
                    .header("X-Internal-Call", "mall-job")
                    .retrieve()
                    .body(Map.class);
            log.info("XXL-JOB: 拼团超时处理完成");
        } catch (Exception e) {
            log.error("XXL-JOB: 拼团超时处理失败: {}", e.getMessage());
            throw new RuntimeException("拼团超时处理失败", e);
        }
    }

    /**
     * 订单超时取消：扫描超时未支付的订单并自动取消。
     * 由 XXL-JOB 调度中心每 1 分钟触发一次。
     */
    @XxlJob("orderTimeoutCancelHandler")
    public void orderTimeoutCancelHandler() {
        log.info("XXL-JOB: 开始执行订单超时取消...");
        try {
            restClient.post()
                    .uri("http://mall-order/orders/timeout-cancel")
                    .header("X-Internal-Call", "mall-job")
                    .retrieve()
                    .body(Map.class);
            log.info("XXL-JOB: 订单超时取消完成");
        } catch (Exception e) {
            log.error("XXL-JOB: 订单超时取消失败: {}", e.getMessage());
            throw new RuntimeException("订单超时取消失败", e);
        }
    }

    /**
     * 优惠券过期处理：扫描过期的优惠券并标记失效。
     */
    @XxlJob("couponExpirationHandler")
    public void couponExpirationHandler() {
        log.info("XXL-JOB: 开始执行优惠券过期处理...");
        try {
            restClient.post()
                    .uri("http://mall-coupon/coupons/expire-batch")
                    .header("X-Internal-Call", "mall-job")
                    .retrieve()
                    .body(Map.class);
            log.info("XXL-JOB: 优惠券过期处理完成");
        } catch (Exception e) {
            log.error("XXL-JOB: 优惠券过期处理失败: {}", e.getMessage());
            throw new RuntimeException("优惠券过期处理失败", e);
        }
    }

    /**
     * 生命树情绪环境扫描：聚合树洞窗口情绪并流转世界树环境状态
     * （心愿宇宙文档 2.2 气象情绪联动：mood &lt; -0.6 下雨 / 好转或
     * BLESS 突增触发彩虹）。由 XXL-JOB 调度中心每 5 分钟触发一次。
     *
     * <p>注意：mall-wish 的内部调用认证仅识别 {@code X-Internal-Call: true}
     * （InternalCallAuthenticationFilter），与其他服务的 "mall-job" 值不同。</p>
     */
    @XxlJob("treeMoodScanHandler")
    public void treeMoodScanHandler() {
        log.info("XXL-JOB: 开始执行生命树情绪环境扫描...");
        try {
            restClient.post()
                    .uri("http://mall-wish/internal/tree-env/scan")
                    .header("X-Internal-Call", "true")
                    .retrieve()
                    .body(Map.class);
            log.info("XXL-JOB: 生命树情绪环境扫描完成");
        } catch (Exception e) {
            log.error("XXL-JOB: 生命树情绪环境扫描失败: {}", e.getMessage());
            throw new RuntimeException("生命树情绪环境扫描失败", e);
        }
    }

    /**
     * 心愿 OVERDUE 状态机扫描：流转 expected_at 过期的 ACTIVE 心愿为 OVERDUE。
     * 由 XXL-JOB 调度中心每日 00:30 触发（心愿宇宙文档 1.2 定时任务矩阵）。
     *
     * <p>注意：mall-wish 的内部调用认证仅识别 {@code X-Internal-Call: true}
     * （InternalCallAuthenticationFilter），与其他服务的 "mall-job" 值不同。</p>
     */
    @XxlJob("wishOverdueScanHandler")
    public void wishOverdueScanHandler() {
        log.info("XXL-JOB: 开始执行心愿 OVERDUE 扫描...");
        try {
            restClient.post()
                    .uri("http://mall-wish/internal/jobs/overdue-scan")
                    .header("X-Internal-Call", "true")
                    .retrieve()
                    .body(Map.class);
            log.info("XXL-JOB: 心愿 OVERDUE 扫描完成");
        } catch (Exception e) {
            log.error("XXL-JOB: 心愿 OVERDUE 扫描失败: {}", e.getMessage());
            throw new RuntimeException("心愿 OVERDUE 扫描失败", e);
        }
    }

    /**
     * 热门推荐缓存刷新：删除 wish:hot:feed 强制回源最新候选集
     * （ZSet 增量回填不清旧成员，需定时清理防止过期心愿残留）。
     * 由 XXL-JOB 调度中心每 10 分钟触发（与缓存 TTL 对齐）。
     */
    @XxlJob("homeHotCacheRefreshHandler")
    public void homeHotCacheRefreshHandler() {
        log.info("XXL-JOB: 开始刷新热门推荐缓存...");
        try {
            restClient.post()
                    .uri("http://mall-wish/internal/jobs/hot-cache-refresh")
                    .header("X-Internal-Call", "true")
                    .retrieve()
                    .body(Map.class);
            log.info("XXL-JOB: 热门推荐缓存刷新完成");
        } catch (Exception e) {
            log.error("XXL-JOB: 热门推荐缓存刷新失败: {}", e.getMessage());
            throw new RuntimeException("热门推荐缓存刷新失败", e);
        }
    }

    /**
     * 生命树季节落库扫描：按 UTC 日期判定季节写入 wish_world_tree_state.season
     * （心愿宇宙文档 Sprint 2.2 动态环境：3-5 月春/6-8 月夏/9-11 月秋/12-2 月冬）。
     * 由 XXL-JOB 调度中心每日 00:00 触发。幂等：季节未变化不产生写。
     *
     * <p>注意：mall-wish 的内部调用认证仅识别 {@code X-Internal-Call: true}
     * （InternalCallAuthenticationFilter），与其他服务的 "mall-job" 值不同。</p>
     */
    @XxlJob("seasonScanHandler")
    public void seasonScanHandler() {
        log.info("XXL-JOB: 开始执行生命树季节落库扫描...");
        try {
            restClient.post()
                    .uri("http://mall-wish/internal/tree-env/season-scan")
                    .header("X-Internal-Call", "true")
                    .retrieve()
                    .body(Map.class);
            log.info("XXL-JOB: 生命树季节落库扫描完成");
        } catch (Exception e) {
            log.error("XXL-JOB: 生命树季节落库扫描失败: {}", e.getMessage());
            throw new RuntimeException("生命树季节落库扫描失败", e);
        }
    }

    /**
     * 徽章漏发补偿扫描：游标分批遍历用户统计记录，逐用户重判定徽章。
     * 补偿 total_helped MQ 消费失败重试耗尽进 DLQ 时的徽章漏发。
     * 由 XXL-JOB 调度中心每日 03:00 低峰触发（与 00:30 OVERDUE 扫描错峰）。
     */
    @XxlJob("badgeCompensationScanHandler")
    public void badgeCompensationScanHandler() {
        log.info("XXL-JOB: 开始执行徽章漏发补偿扫描...");
        try {
            restClient.post()
                    .uri("http://mall-wish/internal/jobs/badge-compensation-scan")
                    .header("X-Internal-Call", "true")
                    .retrieve()
                    .body(Map.class);
            log.info("XXL-JOB: 徽章漏发补偿扫描完成");
        } catch (Exception e) {
            log.error("XXL-JOB: 徽章漏发补偿扫描失败: {}", e.getMessage());
            throw new RuntimeException("徽章漏发补偿扫描失败", e);
        }
    }

    /**
     * 时间胶囊到期扫描：分批 500 条 CAS 流转 SEALED→AVAILABLE
     * （open_at ≤ NOW()，UTC 判定，跨时区旅行不影响到期）并对流转成功者
     * 推送到期待开启通知（心愿宇宙文档 9.2 / Sprint 2.4）。
     * 由 XXL-JOB 调度中心每 10 分钟触发。幂等：重复扫描不重复推送。
     *
     * <p>注意：mall-wish 的内部调用认证仅识别 {@code X-Internal-Call: true}
     * （InternalCallAuthenticationFilter），与其他服务的 "mall-job" 值不同。</p>
     */
    @XxlJob("capsuleOpenScanHandler")
    public void capsuleOpenScanHandler() {
        log.info("XXL-JOB: 开始执行时间胶囊到期扫描...");
        try {
            restClient.post()
                    .uri("http://mall-wish/internal/jobs/capsule-open-scan")
                    .header("X-Internal-Call", "true")
                    .retrieve()
                    .body(Map.class);
            log.info("XXL-JOB: 时间胶囊到期扫描完成");
        } catch (Exception e) {
            log.error("XXL-JOB: 时间胶囊到期扫描失败: {}", e.getMessage());
            throw new RuntimeException("时间胶囊到期扫描失败", e);
        }
    }
}
