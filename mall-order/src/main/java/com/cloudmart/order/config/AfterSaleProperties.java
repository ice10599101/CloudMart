package com.cloudmart.order.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 售后业务配置（TRADE-01）。
 *
 * <p>Spring 宽松绑定下 yml 可写 {@code afterSale.applyWindowDays} 或
 * {@code after-sale.apply-window-days}，两者等价。</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "after-sale")
public class AfterSaleProperties {

    /**
     * COMPLETED 订单售后申请窗口：确认收货后 N 天内可申请（超出拒绝）。
     * 7 天为产品默认值，不是法规结论；按实际运营口径调整。
     */
    private int applyWindowDays = 7;
}
