package com.cloudmart.payment.channel;

import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 渠道通知签名器（PAY-01）：MOCK 渠道用 HMAC-SHA256 对通知载荷签名，
 * 回调侧验签后才处理——伪造/篡改/重放的回调在入口即被拒绝。
 * 真实渠道（支付宝/微信）接入后由对应 adapter 替换验签实现，语义不变：
 * 验签 → 核对金额/商户号 → CAS 状态 → 幂等登记。
 */
public class MockChannelSigner {

    private final byte[] secret;

    public MockChannelSigner(String secret) {
        this.secret = secret == null || secret.isBlank()
                ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
    }

    /** 生成一次签名完整的通知（notificationId 唯一，amount 便于回调侧核对） */
    public SignedNotification sign(String merchantPaymentNo, String amount, String providerTxnNo) {
        String notificationId = UUID.randomUUID().toString();
        String payload = payloadOf(merchantPaymentNo, amount, providerTxnNo, notificationId);
        return new SignedNotification(notificationId, amount, providerTxnNo, sign(payload));
    }

    /** 验签 + 核对商户支付号/金额与载荷一致 */
    public boolean verify(String merchantPaymentNo, String amount, String providerTxnNo,
                          String notificationId, String signature) {
        if (secret.length == 0 || merchantPaymentNo == null || amount == null
                || notificationId == null || signature == null) {
            return false;
        }
        String expected = sign(payloadOf(merchantPaymentNo, amount, providerTxnNo, notificationId));
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("通知签名计算失败", e);
        }
    }

    private String payloadOf(String merchantPaymentNo, String amount, String providerTxnNo, String notificationId) {
        return merchantPaymentNo + "|" + amount + "|" + providerTxnNo + "|" + notificationId;
    }

    public record SignedNotification(String notificationId, String amount, String providerTxnNo, String signature) {
    }
}
