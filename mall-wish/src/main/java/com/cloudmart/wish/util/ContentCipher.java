package com.cloudmart.wish.util;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.config.WishCryptoProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 敏感内容字段加密组件（B21 重写）：树洞对话 + DIARY 成长记录，AES-256-GCM。
 *
 * <p>v2 envelope：{@code enc:v2:<keyId>:<base64(iv||ciphertext)>}，GCM AAD 绑定
 * 记录类型/归属上下文（防密文挪用）；keyId 支撑密钥轮换期新旧密文并存——
 * 当前密钥写，当前 + previous 双钥读（回填验收完成后移除 previous）。</p>
 *
 * <p>兼容与失败语义：</p>
 * <ul>
 *   <li>非 {@code enc:} 开头的历史明文原样返回（无需存量迁移）；</li>
 *   <li>v1 历史密文按当前密钥无 AAD 读取（兼容既有数据）；</li>
 *   <li>解密失败抛 {@code WISH_CONTENT_UNAVAILABLE}——绝不把密文当正文返回；</li>
 *   <li>{@code requireKey=true}（生产）时密钥缺失则启动失败、加密入口抛异常回滚写操作，
 *       绝不降级明文落库；开发/测试可显式 requireKey=false 透传（与 prod 配置隔离）。</li>
 * </ul>
 */
@Component
@Slf4j
public class ContentCipher {

    private static final String V2_PREFIX = "enc:v2:";
    private static final String V1_PREFIX = "enc:v1:";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;
    public static final String ERR_UNAVAILABLE = "WISH_CONTENT_UNAVAILABLE";

    private final SecretKey key;
    private final String keyId;
    private final SecretKey previousKey;
    private final boolean requireKey;
    private final SecureRandom random = new SecureRandom();

    public ContentCipher(WishCryptoProperties properties) {
        this.key = initKey(properties.getKeyBase64(), "WISH_CRYPTO_KEY");
        this.keyId = properties.getKeyId() == null || properties.getKeyId().isBlank()
                ? "k1" : properties.getKeyId().trim();
        this.previousKey = initKey(properties.getPreviousKeyBase64(), "WISH_CRYPTO_PREVIOUS_KEY");
        this.requireKey = properties.isRequireKey();
        if (this.key == null) {
            if (requireKey) {
                throw new IllegalStateException(
                        "B21 fail-closed：wish.crypto.require-key=true 但 WISH_CRYPTO_KEY 缺失/非法，"
                                + "私密内容写功能不可就绪（拒绝启动）");
            }
            log.warn("字段加密未启用：WISH_CRYPTO_KEY 未配置，树洞/DIARY 内容明文落库"
                    + "（仅限开发/测试；生成方式：openssl rand -base64 32）");
        }
    }

    private SecretKey initKey(String keyBase64, String source) {
        if (keyBase64 == null || keyBase64.isBlank()) {
            return null;
        }
        try {
            byte[] raw = Base64.getDecoder().decode(keyBase64.trim());
            if (raw.length != 32) {
                log.warn("{} 解码后长度为 {} 字节，要求 32 字节，密钥不启用", source, raw.length);
                return null;
            }
            return new SecretKeySpec(raw, "AES");
        } catch (IllegalArgumentException ex) {
            log.warn("{} 不是合法 Base64，密钥不启用", source);
            return null;
        }
    }

    public boolean isEnabled() {
        return key != null;
    }

    /** 加密（默认 AAD）；null/已加密/未启用（非强制）时原样返回 */
    public String encrypt(String plain) {
        return encrypt(plain, "LEGACY");
    }

    /** 加密；AAD 绑定记录类型/归属上下文 */
    public String encrypt(String plain, String aad) {
        if (plain == null || plain.isEmpty() || plain.startsWith("enc:")) {
            return plain;
        }
        if (key == null) {
            if (requireKey) {
                // B21：加密不可用即回滚写操作，绝不降级明文
                throw new BusinessException(ERR_UNAVAILABLE, "内容加密不可用，私密写入被拒绝");
            }
            return plain;
        }
        try {
            return V2_PREFIX + keyId + ":" + encryptToBase64(plain, key, aad);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("字段加密失败: {}", ex.getMessage());
            throw new BusinessException(ERR_UNAVAILABLE, "内容加密失败，写入已回滚");
        }
    }

    private String encryptToBase64(String plain, SecretKey secretKey, String aad) throws Exception {
        byte[] iv = new byte[IV_LEN];
        random.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
        if (aad != null) {
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
        }
        byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        byte[] out = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(ct, 0, out, iv.length, ct.length);
        return Base64.getEncoder().encodeToString(out);
    }

    /** 解密（默认 AAD）；历史明文原样返回 */
    public String decrypt(String stored) {
        return decrypt(stored, "LEGACY");
    }

    /**
     * 解密：v2 按 envelope keyId 选钥并校验 AAD；v1 按当前密钥无 AAD 读取；
     * 非密文原样返回；失败抛 {@code WISH_CONTENT_UNAVAILABLE}（不把密文当正文）。
     */
    public String decrypt(String stored, String aad) {
        if (stored == null || !stored.startsWith("enc:")) {
            return stored;
        }
        try {
            if (stored.startsWith(V2_PREFIX)) {
                String rest = stored.substring(V2_PREFIX.length());
                int sep = rest.indexOf(':');
                if (sep <= 0) {
                    throw new IllegalStateException("v2 envelope 非法");
                }
                String envelopeKeyId = rest.substring(0, sep);
                String payload = rest.substring(sep + 1);
                SecretKey candidate = envelopeKeyId.equals(this.keyId) ? key : previousKey;
                if (candidate == null) {
                    throw new IllegalStateException("无 keyId=" + envelopeKeyId + " 对应密钥");
                }
                return decryptFromBase64(payload, candidate, aad);
            }
            if (stored.startsWith(V1_PREFIX)) {
                if (key == null) {
                    throw new IllegalStateException("v1 密文存在但当前未配置密钥");
                }
                return decryptFromBase64(stored.substring(V1_PREFIX.length()), key, null);
            }
            return stored;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("[B21] 字段解密失败（keyId 不匹配/AAD 不符/密文损坏）: {}", ex.getMessage());
            throw new BusinessException(ERR_UNAVAILABLE, "内容暂时不可读（解密失败），已上报排查");
        }
    }

    private String decryptFromBase64(String payload, SecretKey secretKey, String aad) throws Exception {
        byte[] all = Base64.getDecoder().decode(payload);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, all, 0, IV_LEN));
        if (aad != null) {
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
        }
        byte[] plain = cipher.doFinal(all, IV_LEN, all.length - IV_LEN);
        return new String(plain, StandardCharsets.UTF_8);
    }

    /** 成长记录内容按需加密：仅 DIARY 落加密；AAD 绑定心愿上下文（B21） */
    public String encryptGrowth(boolean isDiary, String aad, String content) {
        return isDiary ? encrypt(content, aad) : content;
    }

    /** 成长记录内容按需解密：DIARY 才尝试；AAD 与加密侧一致 */
    public String decryptGrowth(boolean isDiary, String aad, String stored) {
        return isDiary ? decrypt(stored, aad) : stored;
    }
}
