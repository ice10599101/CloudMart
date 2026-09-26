package com.cloudmart.wish.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 敏感内容字段加密配置（规格 4063-4069：树洞内容 + DIARY 成长记录 AES-256-GCM 字段级加密）。
 *
 * <p>密钥经环境变量/Nacos 注入（WISH_CRYPTO_KEY = 32 字节随机数的 Base64，
 * 生成方式：openssl rand -base64 32）。密钥缺失时加密关闭、内容明文落库并打 WARN——
 * 保证开发环境无 Key 也能运行；生产必须配置，且配置后不可丢失（丢失即历史数据不可解）。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "wish.crypto")
public class WishCryptoProperties {

    /** 是否启用字段加密（默认 true；但无有效密钥时实际不加密） */
    private boolean enabled = true;

    /** AES-256 密钥，Base64 编码的 32 字节（256 位） */
    private String keyBase64 = "";

    /** 当前密钥标识（B21：v2 envelope 携带 keyId，支撑轮换期新旧密文并存） */
    private String keyId = "k1";

    /** 轮换期旧密钥（B21：仅用于读旧密文；回填验收完成后移除） */
    private String previousKeyBase64 = "";

    /**
     * B21 fail-closed：true 时密钥缺失/非法则私密内容写不可就绪——
     * 加密入口抛异常回滚写操作，绝不降级明文落库。生产必须置 true
     * （环境变量 WISH_CRYPTO_REQUIRE_KEY=true）；开发/测试显式为 false。
     */
    private boolean requireKey = false;
}
