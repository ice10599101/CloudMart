package com.cloudmart.wms.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wms.dto.ShippingTrackingDTO;
import com.cloudmart.wms.service.LogisticsTrackingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * WMS-01：生产环境物流轨迹实现——未接入真实物流商前明确失败（带 provider/source
 * 语义的空结果），绝不返回编造轨迹；接入快递100/顺丰开放平台后替换本实现。
 */
@Slf4j
@Service
@Profile("prod")
public class ProductionLogisticsTrackingService implements LogisticsTrackingService {

    @Override
    public List<ShippingTrackingDTO> queryTracking(String shippingNo, String carrier) {
        log.warn("[WMS01] 生产环境物流轨迹未接入物流商, shippingNo={}, carrier={}", shippingNo, carrier);
        throw new BusinessException("LOGISTICS_TRACKING_UNAVAILABLE", "物流轨迹查询暂未接入，请稍后重试");
    }
}
