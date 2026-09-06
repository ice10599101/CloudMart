package com.cloudmart.wish.util;

import com.cloudmart.wish.config.WishCryptoProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 敏感内容字段加密组件（规格 4063-4069）：树洞对话内容 + DIARY 成长记录内容，
 * AES-256-GCM，随机 IV 前置，密文带 {@code enc:v1:} 前缀落库。
 *
 * <p>兼容策略：读取时非 {@code enc:v1:} 开头的历史明文原样返回（无需存量迁移）；
 * 解密失败（密钥更换/密文损坏）原样返回并打 WARN，读路径永不因解密失败而中断。
 * 密钥未配置时整体降级为透传（明文落库），启动时打 WARN 提示。</p>
 */
@Component
@Slf4j
public class ContentCipher {

    private static final String PREFIX = "enc:v1:";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public ContentCipher(WishCryptoProperties properties) {
        this.key = initKey(properties);
        if (this.key == null) {
            log.warn("字段加密未启用：WISH_CRYPTO_KEY 未配置或非法，树洞/DIARY 内容将明文落库"
                    + "（生成方式：openssl rand -base64 32）");
        }
    }

    private SecretKey initKey(WishCryptoProperties properties) {
        if (!properties.isEnabled() || properties.getKeyBase64() == null
                || properties.getKeyBase64().isBlank()) {
            return null;
        }
        try {
            byte[] raw = Base64.getDecoder().decode(properties.getKeyBase64().trim());
            if (raw.length != 32) {
                log.warn("WISH_CRYPTO_KEY 解码后长度为 {} 字节，要求 32 字节，字段加密未启用", raw.length);
                return null;
            }
            return new SecretKeySpec(raw, "AES");
        } catch (IllegalArgumentException ex) {
            log.warn("WISH_CRYPTO_KEY 不是合法 Base64，字段加密未启用");
            return null;
        }
    }

    public boolean isEnabled() {
        return key != null;
    }

    /** 加密；null/已加密/未启用时原样返回 */
    public String encrypt(String plain) {
        if (key == null || plain == null || plain.isEmpty() || plain.startsWith(PREFIX)) {
            return plain;
        }
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception ex) {
            log.error("字段加密失败，降级明文落库: {}", ex.getMessage());
            return plain;
        }
    }

    /** 解密；null/非本组件密文/未启用/解密失败时原样返回（读路径永不中断） */
    public String decrypt(String stored) {
        if (key == null || stored == null || !stored.startsWith(PREFIX)) {
            return stored;
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_LEN));
            byte[] plain = cipher.doFinal(all, IV_LEN, all.length - IV_LEN);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log.warn("字段解密失败（密钥不匹配或密文损坏），原样返回: {}", ex.getMessage());
            return stored;
        }
    }

    /** 成长记录内容按需加密：仅 DIARY 类型落加密（规格 4063-4069） */
    public String encryptGrowth(boolean isDiary, String content) {
        return isDiary ? encrypt(content) : content;
    }

    /** 成长记录内容按需解密：DIARY 类型才尝试解密 */
    public String decryptGrowth(boolean isDiary, String stored) {
        return isDiary ? decrypt(stored) : stored;
    }
}
