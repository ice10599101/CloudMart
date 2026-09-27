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

        if (requirement != null && token != null && !token.isBlank() && validationAvailable
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                ServiceTokenCodec.ServiceTokenClaims claims = ServiceTokenCodec.verify(
                        token, properties.getServiceTokenSecret(), properties.getServiceId(),
                        null, requirement.scope(),
                        clock.instant(), Duration.ofSeconds(properties.getClockSkewSeconds()));
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

        filterChain.doFilter(request, response);
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
