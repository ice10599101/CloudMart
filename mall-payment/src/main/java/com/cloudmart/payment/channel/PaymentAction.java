package com.cloudmart.payment.channel;

/**
 * R09 收单动作类型：端侧只按本枚举解释创建尝试响应，禁止猜测 payParams。
 * <ul>
 *   <li>REDIRECT：跳转收银台/回跳（H5、APP 网关支付）；</li>
 *   <li>QR：展示二维码等待扫码；</li>
 *   <li>SDK：端内 SDK 调起（如微信 JSAPI / APP SDK）；</li>
 *   <li>NONE：无外部收单交互（MOCK 测试渠道）。</li>
 * </ul>
 */
public enum PaymentAction {
    REDIRECT,
    QR,
    SDK,
    NONE
}
