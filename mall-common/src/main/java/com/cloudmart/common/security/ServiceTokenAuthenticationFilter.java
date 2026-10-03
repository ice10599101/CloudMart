package com.cloudmart.common.security;

import com.cloudmart.common.security.ServiceTokenCodec.ServiceTokenException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 服务间调用认证过滤器（SEC-01）：只认 {@link ServiceTokenCodec#HEADER_NAME}
 * 短期签名令牌，不再信任裸 {@code X-Internal-Call} 头。
 *
 * <p>入站路径 → (允许签发方, 能力域) 强映射来自
 * {@link CloudmartSecurityProperties#getServiceTokenPaths()}，最长前缀优先。
 * 令牌签名、aud、exp 由 {@link ServiceTokenCodec} 校验，iss 白名单与 scope
 * 由本过滤器按路径要求核对；任一不匹配即拒绝建立 INTERNAL 身份，受保护端点
 * 随后由 Spring Security 401。用户请求无需携带服务令牌；携带无效令牌不会获得
 * 任何身份。</p>
 *
 * <p>密钥不可用时进入 fail-closed：拒绝所有服务令牌（绝不放行未验签调用）。</p>
 */
public class ServiceTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ServiceTokenAuthenticationFilter.class);

    public static final String ROLE_INTERNAL = "ROLE_INTERNAL";

    private final CloudmartSecurityProperties properties;
    private final Clock clock;
    // 静默跳过分支（密钥不可用/路径未映射）原本 401 且零日志，线上无从排查；
    // 各告警一次，避免日志风暴的同时保证"为什么没建立 INTERNAL 身份"可观测
    private final AtomicBoolean warnedValidationUnavailable = new AtomicBoolean(false);
    private final AtomicBoolean warnedUnmappedPath = new AtomicBoolean(false);

    public ServiceTokenAuthenticationFilter(CloudmartSecurityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        CloudmartSecurityProperties.ServiceTokenPath requirement =
                resolveRequirement(request.getRequestURI());
        String token = request.getHeader(ServiceTokenCodec.HEADER_NAME);

        // 密钥不可用时 fail-closed：拒绝所有服务令牌
        boolean validationAvailable = properties.isServiceTokenValidationAvailable();

        if (requirement != null && token != null && !token.isBlank()) {
            if (!validationAvailable) {
                if (warnedValidationUnavailable.compareAndSet(false, true)) {
                    log.warn("[SEC01] 服务令牌验签不可用（密钥未配置或不足{}字符）：path={} 的服务调用将被拒绝"
                            + "（fail-closed）。请注入 CLOUDMART_SERVICE_TOKEN_SECRET（≥{}字符）并重启本服务。",
                            ServiceTokenCodec.MIN_SECRET_LENGTH, request.getRequestURI(),
                            ServiceTokenCodec.MIN_SECRET_LENGTH);
                }
            } else if (SecurityContextHolder.getContext().getAuthentication() == null) {
                try {
                    ServiceTokenCodec.ServiceTokenClaims claims = verifyWithRotation(
                            token, requirement, request.getRequestURI());
                    if (!requirement.issuers().contains(claims.issuer())) {
                        log.warn("[SEC01 REJECT] 服务令牌签发方不在允许列表 path={} issuer={} allowed={}",
                                request.getRequestURI(), claims.issuer(), requirement.issuers());
                    } else {
                        UsernamePasswordAuthenticationToken authentication =
                                UsernamePasswordAuthenticationToken.authenticated(
                                        claims.issuer(), null, List.of(new SimpleGrantedAuthority(ROLE_INTERNAL)));
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                } catch (ServiceTokenException e) {
                    // 拒绝但不中断：受保护端点将得到 401；公开端点保持匿名语义
                    log.warn("[SEC01 REJECT] 服务令牌校验失败 path={} error={} detail={}",
                            request.getRequestURI(), e.getError(), e.getMessage());
                }
            }
        } else if (requirement == null && token != null && !token.isBlank()
                && warnedUnmappedPath.compareAndSet(false, true)) {
            // 调用方为本服务签名但路径未映射：多为 inbound service-token-paths 与
            // 调用方 outbound-scopes 配置不对称（或目标服务写错），静默 401 无法定位
            log.warn("[SEC01] 收到服务令牌但路径未配置校验要求（不建立 INTERNAL 身份）path={}。"
                    + "请核对本服务 cloudmart.security.service-token-paths 与调用方 outbound-scopes 是否对称。",
                    request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }

    /**
     * S05 密钥轮换：先用当前密钥验签；失败且配置了上一代密钥（≥32 字节）时
     * 以旧代密钥重验——旧代令牌在过渡窗口内仍可消费，签发侧永远只用当前
     * 密钥，滚动重启完成后撤掉 previous 即完成轮换。
     */
    private ServiceTokenCodec.ServiceTokenClaims verifyWithRotation(
            String token, CloudmartSecurityProperties.ServiceTokenPath requirement, String requestUri)
            throws ServiceTokenCodec.ServiceTokenException {
        try {
            return ServiceTokenCodec.verify(
                    token, properties.getServiceTokenSecret(), properties.getServiceId(),
                    null, requirement.scope(),
                    clock.instant(), Duration.ofSeconds(properties.getClockSkewSeconds()));
        } catch (ServiceTokenCodec.ServiceTokenException primary) {
            String previous = properties.getServiceTokenSecretPrevious();
            if (previous == null || previous.length() < ServiceTokenCodec.MIN_SECRET_LENGTH) {
                throw primary;
            }
            try {
                ServiceTokenCodec.ServiceTokenClaims legacy = ServiceTokenCodec.verify(
                        token, previous, properties.getServiceId(),
                        null, requirement.scope(),
                        clock.instant(), Duration.ofSeconds(properties.getClockSkewSeconds()));
                log.warn("[SEC01 ROTATION] 令牌由上一代密钥签发（验签通过，轮换过渡中） path={} issuer={}",
                        requestUri, legacy.issuer());
                return legacy;
            } catch (ServiceTokenCodec.ServiceTokenException ignored) {
                // 新旧密钥均验签失败：按当前密钥的原始失败语义处理
                throw primary;
            }
        }
    }

    /**
     * @return 该路径的令牌要求；不在受保护前缀内返回 null（无需服务令牌）。最长前缀优先。
     */
    CloudmartSecurityProperties.ServiceTokenPath resolveRequirement(String requestUri) {
        if (requestUri == null) {
            return null;
        }
        String path = requestUri;
        int semicolon = path.indexOf(';');
        if (semicolon >= 0) {
            path = path.substring(0, semicolon);
        }
        CloudmartSecurityProperties.ServiceTokenPath best = null;
        for (CloudmartSecurityProperties.ServiceTokenPath candidate : properties.getServiceTokenPaths()) {
            String prefix = candidate.prefix();
            if (prefix == null || prefix.isBlank()) {
                continue;
            }
            boolean matches = path.equals(prefix) || path.startsWith(prefix + "/");
            if (!matches) {
                continue;
            }
            if (best == null || prefix.length() > best.prefix().length()) {
                best = candidate;
            }
        }
        return best;
    }
}
