package com.cloudmart.wms.service.impl;

import com.cloudmart.wms.dto.ShippingTrackingDTO;
import com.cloudmart.wms.service.LogisticsTrackingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 物流轨迹对接服务实现。
 * 当前为模拟实现，返回模拟轨迹数据。
 * 生产环境需替换为真实物流 API 调用（如快递100、顺丰开放平台）。
 *
 * 对接步骤：
 * 1. 注册快递100/顺丰开放平台获取 API Key
 * 2. 在 application.yml 配置 api-key 和 customer-id
 * 3. 替换 queryTracking 方法中的模拟逻辑为真实 API 调用
 */
@Service
public class LogisticsTrackingServiceImpl implements LogisticsTrackingService {

    private static final Logger log = LoggerFactory.getLogger(LogisticsTrackingServiceImpl.class);

    @Override
    public List<ShippingTrackingDTO> queryTracking(String shippingNo, String carrier) {
        log.info("Querying logistics tracking: shippingNo={}, carrier={}", shippingNo, carrier);

        // 模拟物流轨迹数据
        LocalDateTime now = LocalDateTime.now();
        return List.of(
                new ShippingTrackingDTO(null, null, "北京市",
                        "包裹已签收，签收人：本人", now.minusHours(2), now, now),
                new ShippingTrackingDTO(null, null, "北京市朝阳区",
                        "快件已派送，派送员：张师傅，电话：13800138000", now.minusHours(5), now, now),
                new ShippingTrackingDTO(null, null, "北京市转运中心",
                        "快件已到达北京市转运中心", now.minusHours(12), now, now),
                new ShippingTrackingDTO(null, null, "上海市转运中心",
                        "快件已从上海市转运中心发出，下一站：北京市转运中心", now.minusHours(24), now, now),
                new ShippingTrackingDTO(null, null, "上海市",
                        "已揽收", now.minusHours(36), now, now)
        );
    }
}
