package com.cloudmart.wish.util;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.config.WishCryptoProperties;

import java.security.SecureRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * B21 加密组件测试（T24 语义）：v2 envelope 往返、AAD 绑定防挪用、keyId 轮换双钥读、
 * 篡改密文可识别不可用（不把密文当正文）、fail-closed 生产语义。
 */
@DisplayName("ContentCipher v2 envelope（B21）")
class ContentCipherTest {

    private WishCryptoProperties props(String key, String previousKey, boolean requireKey) {
        WishCryptoProperties properties = new WishCryptoProperties();
        properties.setEnabled(true);
        properties.setKeyBase64(key);
        properties.setKeyId("k2");
        properties.setPreviousKeyBase64(previousKey == null ? "" : previousKey);
        properties.setRequireKey(requireKey);
        return properties;
    }

    private static final String KEY_A = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY";
    private static final String KEY_B = "WFBPTlZPSUQxMjM0NTY3ODlhYmNkZWYwMTIzNDU2Nzg";

    @Test
    @DisplayName("v2 往返：envelope 携带 keyId；AAD 一致才可解")
    void roundTrip_withAad() {
        ContentCipher cipher = new ContentCipher(props(KEY_A, null, false));
        String stored = cipher.encrypt("私密日记", "GROWTH:100:7");

        assertThat(stored).startsWith("enc:v2:k2:");
        assertThat(cipher.decrypt(stored, "GROWTH:100:7")).isEqualTo("私密日记");
    }

    @Test
    @DisplayName("AAD 不符 → WISH_CONTENT_UNAVAILABLE（密文挪用被拦截）")
    void wrongAad_rejected() {
        ContentCipher cipher = new ContentCipher(props(KEY_A, null, false));
        String stored = cipher.encrypt("私密日记", "GROWTH:100:7");

        assertThatThrownBy(() -> cipher.decrypt(stored, "GROWTH:100:8"))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getCode()).isEqualTo(ContentCipher.ERR_UNAVAILABLE));
    }

    @Test
    @DisplayName("轮换：旧 keyId 密文在配置 previous 后仍可读；新写用新 keyId")
    void rotation_oldKeyStillReadable() {
        // 旧密钥（k1 时期）密文：用 KEY_A 以 keyId=k1 签发
        WishCryptoProperties oldProps = props(KEY_A, null, false);
        oldProps.setKeyId("k1");
        ContentCipher oldCipher = new ContentCipher(oldProps);
        String legacyStored = oldCipher.encrypt("旧日记", "GROWTH:100:7");

        // 轮换后：当前 k2 + previous=KEY_A
        ContentCipher rotated = new ContentCipher(props(KEY_B, KEY_A, false));
        assertThat(rotated.decrypt(legacyStored, "GROWTH:100:7")).isEqualTo("旧日记");

        String fresh = rotated.encrypt("新日记", "GROWTH:100:7");
        assertThat(fresh).startsWith("enc:v2:k2:");
        assertThat(rotated.decrypt(fresh, "GROWTH:100:7")).isEqualTo("新日记");
    }

    @Test
    @DisplayName("v1 历史密文按当前密钥兼容读取")
    void v1LegacyReadable() {
        ContentCipher cipher = new ContentCipher(props(KEY_A, null, false));
        // 手工构造 v1 密文（历史格式）
        String v1 = "enc:v1:" + encryptV1("历史内容", KEY_A);
        assertThat(cipher.decrypt(v1, "GROWTH:1:1")).isEqualTo("历史内容");
    }

    private String encryptV1(String plain, String keyBase64) {
        try {
            javax.crypto.SecretKey key = new javax.crypto.spec.SecretKeySpec(
                    java.util.Base64.getDecoder().decode(keyBase64), "AES");
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key, new javax.crypto.spec.GCMParameterSpec(128, iv));
            byte[] ct = cipher.doFinal(plain.getBytes());
            byte[] out = new byte[12 + ct.length];
            System.arraycopy(iv, 0, out, 0, 12);
            System.arraycopy(ct, 0, out, 12, ct.length);
            return java.util.Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("篡改密文 → WISH_CONTENT_UNAVAILABLE，不返回密文原文")
    void tamperedCiphertext_unavailable() {
        ContentCipher cipher = new ContentCipher(props(KEY_A, null, false));
        String stored = cipher.encrypt("私密日记", "GROWTH:100:7");
        String tampered = stored.substring(0, stored.length() - 4) + "AAAA";

        assertThatThrownBy(() -> cipher.decrypt(tampered, "GROWTH:100:7"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("requireKey=true 无密钥 → 构造即拒绝启动（B21 fail-closed）")
    void requireKey_constructorFails() {
        assertThatThrownBy(() -> new ContentCipher(props("", null, true)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("requireKey=true 有密钥：加密异常路径回滚写（BusinessException）")
    void requireKey_withKey_encrypts() {
        ContentCipher cipher = new ContentCipher(props(KEY_A, null, true));
        String stored = cipher.encrypt("内容", "GROWTH:1:1");
        assertThat(stored).startsWith("enc:v2:k2:");
        assertThat(cipher.decrypt(stored, "GROWTH:1:1")).isEqualTo("内容");
    }

    @Test
    @DisplayName("明文/空值透传（幂等，不二次加密）")
    void plaintextPassthrough() {
        ContentCipher cipher = new ContentCipher(props(KEY_A, null, false));
        assertThat(cipher.encrypt(null, "x")).isNull();
        assertThat(cipher.decrypt("普通明文", "x")).isEqualTo("普通明文");
        String once = cipher.encrypt("内容", "x");
        assertThat(cipher.encrypt(once, "x")).isEqualTo(once);
    }
}
