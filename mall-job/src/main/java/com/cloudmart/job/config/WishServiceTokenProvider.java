package com.cloudmart.job.config;

import com.cloudmart.common.security.ServiceTokenSigner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 * mall-wish 服务令牌签发器（B01）。
 *
 * <p>mall-job 调用 mall-wish 内部任务端点（/internal/jobs、/internal/tree-env）时，
 * 携带本组件签出的短期 HS256 令牌；mall-wish 侧按路径强校验 iss/aud/scope。
 * 旧 {@code X-Internal-Call} 头对 mall-wish 不再构成信任凭证（保留是为兼容
 * 其他尚未完成服务令牌改造的下游）。</p>
 *
 * <p>密钥经部署环境变量 {@code WISH_SERVICE_TOKEN_SECRET} 注入，缺失时启动失败。</p>
 */
@Component
public class WishServiceTokenProvider {

    private final ServiceTokenSigner signer;

    public WishServiceTokenProvider(
            @Value("${wish.service-token.secret:${WISH_SERVICE_TOKEN_SECRET:}}") String secret) {
        this.signer = new ServiceTokenSigner(secret, "mall-job", "wish:jobs", Duration.ofSeconds(60),
                Clock.systemUTC());
    }

    /** 签出一个发往 mall-wish 的新令牌（60 秒有效期 + 30 秒接收方时钟容忍）。 */
    public String token() {
        return signer.sign("mall-wish");
    }
}
