package com.cloudmart.wms.service;

import com.cloudmart.wms.dto.ShippingTrackingDTO;
import java.util.List;

/**
 * 物流轨迹对接服务。
 * 对接第三方物流 API（如快递100、顺丰开放平台）获取物流轨迹，
 * 当前为模拟实现，生产环境需替换为真实 API 调用。
 */
public interface LogisticsTrackingService {

    /**
     * 查询物流轨迹（对接第三方物流 API）
     */
    List<ShippingTrackingDTO> queryTracking(String shippingNo, String carrier);
}
