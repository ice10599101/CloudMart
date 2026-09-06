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
}
