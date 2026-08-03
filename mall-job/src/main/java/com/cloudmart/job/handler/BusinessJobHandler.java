package com.cloudmart.job.handler;

import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 业务定时任务 Handler 集合。
 * 通过 XXL-JOB 统一调度，支持集群环境下的分布式任务执行。
 */
@Component
public class BusinessJobHandler {

    private static final Logger log = LoggerFactory.getLogger(BusinessJobHandler.class);

    private final RestClient restClient;

    public BusinessJobHandler() {
        this.restClient = RestClient.builder().build();
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
}
