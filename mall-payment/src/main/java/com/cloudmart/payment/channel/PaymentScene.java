package com.cloudmart.payment.channel;

import com.cloudmart.common.exception.BusinessException;

/**
 * R09 支付场景（客户端形态）：由各端收银台在渠道查询与创建尝试时显式声明。
 */
public enum PaymentScene {
    H5,
    MINIAPP,
    APP;

    /** 解析外部入参；非法场景抛业务异常（不依赖枚举名匹配报错文案）。 */
    public static PaymentScene fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException("PAYMENT_SCENE_UNSUPPORTED", "支付场景不能为空");
        }
        try {
            return PaymentScene.valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("PAYMENT_SCENE_UNSUPPORTED", "不支持的支付场景: " + code);
        }
    }
}
